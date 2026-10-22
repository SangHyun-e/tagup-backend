package com.tagup.backend.bet.service;

import com.tagup.backend.bet.dto.CreateAtBatBetRequest;
import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetType;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.live.*;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.notification.service.PushSender;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.repository.RoomMemberRepository;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.team.entity.Team;
import com.tagup.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 타석 배팅 생성.
 *
 * <p>핵심은 <b>어느 타석인지를 클라이언트가 정하지 않는다</b>는 것이다. 화면이 낡았을 때
 * 엉뚱한 타석에 걸리거나, 결과를 본 뒤 과거 타석을 지정하는 일을 막기 위해 서버가 정한다.
 */
class AtBatBetCreationTest {

    private BetRepository betRepository;
    private RoomRepository roomRepository;
    private RoomMemberRepository roomMemberRepository;
    private CurrentAtBatRegistry currentAtBats;
    private BetService betService;

    private User proposer;
    private Room room;
    private Game liveGame;

    private static final String KBO_GAME_ID = "20260914_NC_두산";

    @BeforeEach
    void setUp() {
        betRepository = mock(BetRepository.class);
        roomRepository = mock(RoomRepository.class);
        roomMemberRepository = mock(RoomMemberRepository.class);
        currentAtBats = mock(CurrentAtBatRegistry.class);

        betService = new BetService(betRepository, mock(BetChatAnnouncer.class),
                mock(PushSender.class), roomRepository, roomMemberRepository,
                mock(GameRepository.class), currentAtBats);
        ReflectionTestUtils.setField(betService, "atBatWindowSeconds", 30L);

        Team away = withId(Team.builder().name("NC 다이노스").shortName("NC").build(), 9L);
        Team home = withId(Team.builder().name("두산 베어스").shortName("두산").build(), 4L);
        liveGame = withId(Game.builder()
                .kboGameId(KBO_GAME_ID)
                .gameDate(LocalDate.now()).gameTime(LocalTime.of(18, 30))
                .awayTeam(away).homeTeam(home)
                .status(GameStatus.IN_PROGRESS)
                .build(), 683L);

        proposer = withId(User.builder().firebaseUid("p").email("p@t").nickname("제안자").build(), 1L);
        room = withId(Room.builder().tagCode("ABC123").name("더그아웃").createdBy(proposer).build(), 10L);
        room.setWatchingGame(liveGame);

        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(roomMemberRepository.existsByRoomAndUser(room, proposer)).thenReturn(true);
        when(roomMemberRepository.findAllByRoomWithUser(room)).thenReturn(List.of());
        when(betRepository.save(any(Bet.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 어느_타석인지는_서버가_정한다() {
        openAtBat(3, HalfInning.BOTTOM, "박찬호", Instant.now());

        betService.createAtBatBet(10L, request(AtBatResult.OUT), proposer);

        Bet saved = savedBet();
        assertThat(saved.getType()).isEqualTo(BetType.AT_BAT);
        assertThat(saved.getBetOnAtBatResult()).isEqualTo(AtBatResult.OUT);
        assertThat(saved.matchesAtBat(3, HalfInning.BOTTOM, "박찬호")).isTrue();
        assertThat(saved.getBetOnTeamId()).as("타석 배팅에는 응원 팀이 없다").isNull();
    }

    /**
     * 폴링 간격만큼 '결과는 났는데 서버는 모르는' 구간이 생긴다.
     * 중계를 보는 사람이 그 사이에 결과를 알고 거는 것을 막는다.
     */
    @Test
    void 창이_지난_타석에는_걸_수_없다() {
        openAtBat(3, HalfInning.BOTTOM, "박찬호", Instant.now().minusSeconds(31));

        assertThatThrownBy(() -> betService.createAtBatBet(10L, request(AtBatResult.OUT), proposer))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AT_BAT_BETTING_CLOSED);

        verify(betRepository, never()).save(any());
    }

    @Test
    void 창_안이면_걸_수_있다() {
        openAtBat(3, HalfInning.BOTTOM, "박찬호", Instant.now().minusSeconds(29));

        betService.createAtBatBet(10L, request(AtBatResult.SAFE), proposer);

        assertThat(savedBet().getBetOnAtBatResult()).isEqualTo(AtBatResult.SAFE);
    }

    @Test
    void 진행_중인_타석이_없으면_거절한다() {
        when(currentAtBats.find(KBO_GAME_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> betService.createAtBatBet(10L, request(AtBatResult.OUT), proposer))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_LIVE_AT_BAT);
    }

    @Test
    void 관전_경기가_없으면_거절한다() {
        room.setWatchingGame(null);

        assertThatThrownBy(() -> betService.createAtBatBet(10L, request(AtBatResult.OUT), proposer))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_WATCHING_GAME);
    }

    @Test
    void 진행_중이_아닌_경기에는_걸_수_없다() {
        liveGame.updateResult(GameStatus.FINISHED, 5, 3, null);

        assertThatThrownBy(() -> betService.createAtBatBet(10L, request(AtBatResult.OUT), proposer))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GAME_NOT_LIVE);
    }

    @Test
    void 아웃_세이프가_아닌_값은_거절한다() {
        openAtBat(3, HalfInning.BOTTOM, "박찬호", Instant.now());

        assertThatThrownBy(() -> betService.createAtBatBet(10L, request(AtBatResult.UNKNOWN), proposer))
                .as("UNKNOWN 은 정산 결과이지 배팅 대상이 아니다")
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_AT_BAT_RESULT);
    }

    @Test
    void 더그아웃_멤버가_아니면_거절한다() {
        User outsider = withId(User.builder().firebaseUid("x").email("x@t").nickname("외부인").build(), 99L);
        when(roomMemberRepository.existsByRoomAndUser(room, outsider)).thenReturn(false);
        openAtBat(3, HalfInning.BOTTOM, "박찬호", Instant.now());

        assertThatThrownBy(() -> betService.createAtBatBet(10L, request(AtBatResult.OUT), outsider))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ROOM_MEMBER);
    }

    // ------------------------------------------------------------------ 헬퍼

    private void openAtBat(int inning, HalfInning half, String batter, Instant startedAt) {
        when(currentAtBats.find(KBO_GAME_ID))
                .thenReturn(Optional.of(new CurrentAtBat(inning, half, batter, startedAt)));
    }

    private CreateAtBatBetRequest request(AtBatResult on) {
        return new CreateAtBatBetRequest(on, "커피 한 잔");
    }

    private Bet savedBet() {
        ArgumentCaptor<Bet> captor = ArgumentCaptor.forClass(Bet.class);
        verify(betRepository).save(captor.capture());
        return captor.getValue();
    }

    private <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
