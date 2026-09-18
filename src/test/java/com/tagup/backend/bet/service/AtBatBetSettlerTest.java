package com.tagup.backend.bet.service;

import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetResult;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.entity.BetType;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.live.*;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.team.entity.Team;
import com.tagup.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 타석이 끝나는 순간의 배팅 정산.
 *
 * <p>승패 배팅과 달리 경기 종료를 기다리지 않는다. 타석 하나가 끝나면 즉시 갈린다.
 * 판정 불가(UNKNOWN)는 약 6% 발생하며, 억지로 한쪽으로 몰지 않고 무효 처리한다.
 */
class AtBatBetSettlerTest {

    private BetRepository betRepository;
    private BetChatAnnouncer announcer;
    private AtBatBetSettler settler;

    private Game game;
    private User proposer, receiver;
    private Room room;

    private static final long GAME_ID = 683L;

    @BeforeEach
    void setUp() {
        betRepository = mock(BetRepository.class);
        announcer = mock(BetChatAnnouncer.class);
        settler = new AtBatBetSettler(betRepository, announcer);

        Team away = withId(Team.builder().name("NC 다이노스").shortName("NC").build(), 9L);
        Team home = withId(Team.builder().name("두산 베어스").shortName("두산").build(), 4L);
        game = withId(Game.builder()
                .kboGameId("20260914_NC_두산")
                .gameDate(LocalDate.now()).gameTime(LocalTime.of(18, 30))
                .awayTeam(away).homeTeam(home)
                .status(GameStatus.IN_PROGRESS)
                .build(), GAME_ID);

        proposer = withId(User.builder().firebaseUid("p").email("p@t").nickname("제안자").build(), 1L);
        receiver = withId(User.builder().firebaseUid("r").email("r@t").nickname("수락자").build(), 2L);
        room = withId(Room.builder().tagCode("ABC123").name("더그아웃").createdBy(proposer).build(), 10L);
    }

    // ---------------------------------------------------------------- 정산

    @Test
    void 건_결과가_맞으면_제안자가_이긴다() {
        Bet bet = acceptedAtBatBet(AtBatResult.OUT, 3, HalfInning.BOTTOM, "박찬호");
        given(bet);

        settler.onAtBatDetected(event(3, HalfInning.BOTTOM, "박찬호", AtBatResult.OUT));

        assertThat(bet.getStatus()).isEqualTo(BetStatus.FINISHED);
        assertThat(bet.getProposerResult()).isEqualTo(BetResult.WIN);
    }

    @Test
    void 건_결과가_틀리면_제안자가_진다() {
        Bet bet = acceptedAtBatBet(AtBatResult.OUT, 3, HalfInning.BOTTOM, "박찬호");
        given(bet);

        settler.onAtBatDetected(event(3, HalfInning.BOTTOM, "박찬호", AtBatResult.SAFE));

        assertThat(bet.getProposerResult()).isEqualTo(BetResult.LOSE);
    }

    /**
     * KBO 응답만으로는 약 6%의 타석에서 결과를 역산할 수 없다.
     * 억지로 한쪽으로 몰면 틀린 정산이 되므로 무효로 둔다.
     */
    @Test
    void 판정_불가한_타석은_무효_처리한다() {
        Bet onOut = acceptedAtBatBet(AtBatResult.OUT, 5, HalfInning.TOP, "박민우");
        Bet onSafe = acceptedAtBatBet(AtBatResult.SAFE, 5, HalfInning.TOP, "박민우");
        given(onOut, onSafe);

        settler.onAtBatDetected(event(5, HalfInning.TOP, "박민우", AtBatResult.UNKNOWN));

        assertThat(onOut.getProposerResult()).isEqualTo(BetResult.DRAW);
        assertThat(onSafe.getProposerResult()).isEqualTo(BetResult.DRAW);
    }

    // ------------------------------------------------------------ 타석 식별

    @Test
    void 다른_타석에_걸린_배팅은_건드리지_않는다() {
        Bet thisAtBat = acceptedAtBatBet(AtBatResult.OUT, 3, HalfInning.BOTTOM, "박찬호");
        Bet otherBatter = acceptedAtBatBet(AtBatResult.OUT, 3, HalfInning.BOTTOM, "양의지");
        Bet otherInning = acceptedAtBatBet(AtBatResult.OUT, 4, HalfInning.BOTTOM, "박찬호");
        Bet otherHalf = acceptedAtBatBet(AtBatResult.OUT, 3, HalfInning.TOP, "박찬호");
        given(thisAtBat, otherBatter, otherInning, otherHalf);

        settler.onAtBatDetected(event(3, HalfInning.BOTTOM, "박찬호", AtBatResult.OUT));

        assertThat(thisAtBat.getStatus()).isEqualTo(BetStatus.FINISHED);
        assertThat(otherBatter.getStatus()).isEqualTo(BetStatus.ACCEPTED);
        assertThat(otherInning.getStatus()).isEqualTo(BetStatus.ACCEPTED);
        assertThat(otherHalf.getStatus()).isEqualTo(BetStatus.ACCEPTED);
    }

    @Test
    void 타석이_끝날_때까지_아무도_콜하지_않으면_만료된다() {
        Bet pending = atBatBet(AtBatResult.OUT, 3, HalfInning.BOTTOM, "박찬호");  // 콜 없음
        given(pending);

        settler.onAtBatDetected(event(3, HalfInning.BOTTOM, "박찬호", AtBatResult.OUT));

        assertThat(pending.getStatus()).isEqualTo(BetStatus.CANCELLED);
        assertThat(pending.getProposerResult())
                .as("성립하지 않은 배팅은 승패가 없다")
                .isNull();
        verifyNoInteractions(announcer);
    }

    // -------------------------------------------------------------- 견고성

    /** 알림 실패가 정산을 되돌리면 안 된다 (2026-09-09 승패 정산 사고의 교훈) */
    @Test
    void 채팅_공지가_실패해도_정산은_확정된다() {
        Bet bet = acceptedAtBatBet(AtBatResult.SAFE, 7, HalfInning.TOP, "손아섭");
        given(bet);
        doThrow(new RuntimeException("firestore unavailable"))
                .when(announcer).announceSettlement(any(), any());

        settler.onAtBatDetected(event(7, HalfInning.TOP, "손아섭", AtBatResult.SAFE));

        assertThat(bet.getStatus()).isEqualTo(BetStatus.FINISHED);
        assertThat(bet.getProposerResult()).isEqualTo(BetResult.WIN);
    }

    /**
     * 리스너는 폴러 스레드에서 동기 호출된다. 여기서 예외가 새면
     * 그 주기의 나머지 경기 감지까지 죽는다.
     */
    @Test
    void 정산_중_예외가_나도_폴러로_전파되지_않는다() {
        when(betRepository.findAtBatBetsForSettlement(any(), any(), anyList()))
                .thenThrow(new RuntimeException("DB down"));

        assertThatCode(() -> settler.onAtBatDetected(
                event(1, HalfInning.TOP, "박민우", AtBatResult.OUT)))
                .doesNotThrowAnyException();
    }

    @Test
    void 걸린_배팅이_없으면_아무_일도_하지_않는다() {
        when(betRepository.findAtBatBetsForSettlement(any(), any(), anyList()))
                .thenReturn(List.of());

        settler.onAtBatDetected(event(1, HalfInning.TOP, "박민우", AtBatResult.OUT));

        verifyNoInteractions(announcer);
    }

    // ------------------------------------------------------------------ 헬퍼

    private void given(Bet... bets) {
        when(betRepository.findAtBatBetsForSettlement(
                eq(GAME_ID), eq(BetType.AT_BAT),
                eq(List.of(BetStatus.PENDING, BetStatus.ACCEPTED))))
                .thenReturn(List.of(bets));
    }

    private AtBatDetectedEvent event(int inning, HalfInning half, String batter, AtBatResult result) {
        return new AtBatDetectedEvent(GAME_ID, game.getKboGameId(),
                new AtBatEvent(batter, "투수", inning, half, result, 0, false));
    }

    private Bet atBatBet(AtBatResult betOn, int inning, HalfInning half, String batter) {
        return Bet.forAtBat(proposer, room, game, "커피 한 잔", betOn, inning, half, batter);
    }

    private Bet acceptedAtBatBet(AtBatResult betOn, int inning, HalfInning half, String batter) {
        Bet bet = atBatBet(betOn, inning, half, batter);
        bet.accept(receiver);
        return bet;
    }

    private <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
