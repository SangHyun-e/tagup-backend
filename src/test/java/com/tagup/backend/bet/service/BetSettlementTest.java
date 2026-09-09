package com.tagup.backend.bet.service;

import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetResult;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.notification.service.PushSender;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.repository.RoomMemberRepository;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.team.entity.Team;
import com.tagup.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 승패 배팅 정산 검증.
 *
 * <p>정산은 실경기가 끝나야만 도는 경로라 수동 확인이 사실상 불가능하다.
 * 2026-08-26에는 진행 중 경기를 종료로 오판해 <b>모든 배팅이 경기 시작 30분 만에
 * 0:0 무승부로 정산되는</b> 사고가 있었고, 그때 이 구간에 테스트가 하나도 없었다.
 * 그 사고의 두 방어선(종료 아닌 경기는 손대지 않는다 / 스코어가 없으면 보류한다)을
 * 결과 계산과 함께 고정한다.
 */
class BetSettlementTest {

    private BetRepository betRepository;
    private BetChatAnnouncer betChatAnnouncer;
    private PushSender pushSender;
    private BetService betService;

    private Team homeTeam;   // id 1
    private Team awayTeam;   // id 6
    private User proposer;
    private User receiver;
    private Room room;

    private static final long HOME_TEAM_ID = 1L;
    private static final long AWAY_TEAM_ID = 6L;

    @BeforeEach
    void setUp() {
        betRepository = mock(BetRepository.class);
        betChatAnnouncer = mock(BetChatAnnouncer.class);
        pushSender = mock(PushSender.class);

        betService = new BetService(
                betRepository,
                betChatAnnouncer,
                pushSender,
                mock(RoomRepository.class),
                mock(RoomMemberRepository.class),
                mock(GameRepository.class));

        homeTeam = withId(Team.builder().name("두산 베어스").shortName("두산").build(), HOME_TEAM_ID);
        awayTeam = withId(Team.builder().name("SSG 랜더스").shortName("SSG").build(), AWAY_TEAM_ID);

        proposer = User.builder().firebaseUid("uid-p").email("p@test").nickname("제안자").build();
        receiver = User.builder().firebaseUid("uid-r").email("r@test").nickname("수락자").build();

        room = withId(Room.builder().tagCode("ABC123").name("더그아웃").createdBy(proposer).build(), 10L);
    }

    // ---------------------------------------------------------------- 결과 계산

    @Test
    void 홈팀이_이기면_홈에_건_제안자는_승리한다() {
        Bet bet = acceptedBet(HOME_TEAM_ID);
        Game game = finishedGame(5, 3);   // 홈 5 : 원정 3
        given(game, bet);

        betService.settleByGame(game);

        assertThat(bet.getStatus()).isEqualTo(BetStatus.FINISHED);
        assertThat(bet.getProposerResult()).isEqualTo(BetResult.WIN);
    }

    @Test
    void 홈팀이_이기면_원정에_건_제안자는_패배한다() {
        Bet bet = acceptedBet(AWAY_TEAM_ID);
        Game game = finishedGame(5, 3);
        given(game, bet);

        betService.settleByGame(game);

        assertThat(bet.getProposerResult()).isEqualTo(BetResult.LOSE);
    }

    @Test
    void 원정팀이_이기면_원정에_건_제안자는_승리한다() {
        Bet bet = acceptedBet(AWAY_TEAM_ID);
        Game game = finishedGame(2, 7);   // 홈 2 : 원정 7
        given(game, bet);

        betService.settleByGame(game);

        assertThat(bet.getProposerResult()).isEqualTo(BetResult.WIN);
    }

    @Test
    void 동점이면_어느_쪽에_걸었든_무승부다() {
        Bet onHome = acceptedBet(HOME_TEAM_ID);
        Bet onAway = acceptedBet(AWAY_TEAM_ID);
        Game game = finishedGame(4, 4);
        given(game, onHome, onAway);

        betService.settleByGame(game);

        assertThat(onHome.getProposerResult()).isEqualTo(BetResult.DRAW);
        assertThat(onAway.getProposerResult()).isEqualTo(BetResult.DRAW);
    }

    // ------------------------------------------------- 8/26 사고의 방어선 두 개

    /**
     * 1차 방어선. 진행 중 경기의 점수 칸은 "0vs0"으로 채워져 있어
     * 크롤러가 한 번 더 오판하면 0:0 무승부 정산으로 직행한다.
     */
    @Test
    void 종료되지_않은_경기는_배팅을_조회조차_하지_않는다() {
        Game inProgress = Game.builder()
                .kboGameId("20260909_SSG_두산")
                .gameDate(LocalDate.now()).gameTime(LocalTime.of(18, 30))
                .homeTeam(homeTeam).awayTeam(awayTeam)
                .status(GameStatus.IN_PROGRESS)
                .homeScore(0).awayScore(0)
                .build();

        betService.settleByGame(inProgress);

        verify(betRepository, never()).findByGameAndStatusIn(any(), anyList());
        verifyNoInteractions(betChatAnnouncer, pushSender);
    }

    /**
     * 2차 방어선. 크롤링 결손으로 스코어가 비어 있으면 정산하지 않고 다음 주기로 미룬다.
     * 여기서 0으로 넘어가면 역시 무승부가 되어버린다.
     */
    @Test
    void 스코어가_비어있으면_정산을_보류하고_상태를_유지한다() {
        Bet bet = acceptedBet(HOME_TEAM_ID);
        Game noScore = Game.builder()
                .kboGameId("20260909_SSG_두산")
                .gameDate(LocalDate.now()).gameTime(LocalTime.of(18, 30))
                .homeTeam(homeTeam).awayTeam(awayTeam)
                .status(GameStatus.FINISHED)
                .homeScore(null).awayScore(null)
                .build();
        given(noScore, bet);

        betService.settleByGame(noScore);

        assertThat(bet.getStatus())
                .as("다음 정산 주기에 다시 시도해야 하므로 ACCEPTED로 남아야 한다")
                .isEqualTo(BetStatus.ACCEPTED);
        assertThat(bet.getProposerResult()).isNull();
        verifyNoInteractions(betChatAnnouncer, pushSender);
    }

    // ---------------------------------------------------------- 미수락 배팅 만료

    @Test
    void 아무도_콜하지_않은_배팅은_정산이_아니라_만료된다() {
        Bet pending = pendingBet(HOME_TEAM_ID);       // 콜 없음
        Bet accepted = acceptedBet(HOME_TEAM_ID);
        Game game = finishedGame(5, 3);
        given(game, pending, accepted);

        betService.settleByGame(game);

        assertThat(pending.getStatus()).isEqualTo(BetStatus.CANCELLED);
        assertThat(pending.getProposerResult())
                .as("성립하지 않은 배팅은 승패가 없다")
                .isNull();

        // 같은 경기의 성립된 배팅은 정상 정산된다
        assertThat(accepted.getStatus()).isEqualTo(BetStatus.FINISHED);
        assertThat(accepted.getProposerResult()).isEqualTo(BetResult.WIN);

        // 채팅 공지·푸시는 성립된 1건에 대해서만
        verify(betChatAnnouncer, times(1)).announceSettlement(any(), any());
        verify(pushSender, times(1)).sendToUsers(anyList(), any(), any(), any());
    }

    @Test
    void 정산되면_채팅_공지와_푸시가_양측에_발송된다() {
        Bet bet = acceptedBet(HOME_TEAM_ID);
        Game game = finishedGame(5, 3);
        given(game, bet);

        betService.settleByGame(game);

        verify(betChatAnnouncer).announceSettlement(bet, game);
        verify(pushSender).sendToUsers(
                argThat(users -> users.containsAll(List.of(proposer, receiver))),
                eq("배팅 정산 완료"), any(), any());
    }

    // ------------------------------------------------------------------ 헬퍼

    private void given(Game game, Bet... bets) {
        when(betRepository.findByGameAndStatusIn(
                eq(game), eq(List.of(BetStatus.PENDING, BetStatus.ACCEPTED))))
                .thenReturn(List.of(bets));
    }

    private Game finishedGame(int homeScore, int awayScore) {
        return Game.builder()
                .kboGameId("20260909_SSG_두산")
                .gameDate(LocalDate.now()).gameTime(LocalTime.of(18, 30))
                .homeTeam(homeTeam).awayTeam(awayTeam)
                .status(GameStatus.FINISHED)
                .homeScore(homeScore).awayScore(awayScore)
                .build();
    }

    private Bet pendingBet(long betOnTeamId) {
        return Bet.builder()
                .proposer(proposer).room(room).game(finishedGame(0, 0))
                .content("커피 한 잔").betOnTeamId(betOnTeamId)
                .build();
    }

    private Bet acceptedBet(long betOnTeamId) {
        Bet bet = pendingBet(betOnTeamId);
        bet.accept(receiver);
        return bet;
    }

    private <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
