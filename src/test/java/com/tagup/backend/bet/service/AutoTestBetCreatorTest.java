package com.tagup.backend.bet.service;

import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.entity.BetType;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.live.AtBatResult;
import com.tagup.backend.game.live.AtBatStartedEvent;
import com.tagup.backend.game.live.HalfInning;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.team.entity.Team;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 검증 전용 자동 배팅 생성.
 *
 * <p>타석 배팅 정산은 실경기에서만 도는데, 검증하려면 매일 밤 누군가 30초 안에 배팅을 걸고
 * 다른 계정으로 콜까지 해야 한다. 그걸 못 해 9/13·9/15 하루씩 날렸다.
 * 여기서 고정하는 건 <b>사람 손 없이도 검증 데이터가 쌓이고, 그러다 실경기 감지를 망치지 않는다</b>는 것.
 */
class AutoTestBetCreatorTest {

    private BetRepository betRepository;
    private AutoTestBetCreator creator;

    @BeforeEach
    void setUp() {
        betRepository = mock(BetRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GameRepository gameRepository = mock(GameRepository.class);

        Team away = withId(Team.builder().name("NC 다이노스").shortName("NC").build(), 9L);
        Team home = withId(Team.builder().name("두산 베어스").shortName("두산").build(), 4L);
        Game game = withId(Game.builder()
                .kboGameId("20260916_NC_두산")
                .gameDate(LocalDate.now()).gameTime(LocalTime.of(18, 30))
                .awayTeam(away).homeTeam(home).status(GameStatus.IN_PROGRESS).build(), 700L);
        User proposer = withId(User.builder().firebaseUid("p").email("p@t").nickname("제안자").build(), 1L);
        User receiver = withId(User.builder().firebaseUid("r").email("r@t").nickname("수락자").build(), 2L);
        Room room = withId(Room.builder().tagCode("ABC123").name("방").createdBy(proposer).build(), 1L);

        when(gameRepository.findById(700L)).thenReturn(Optional.of(game));
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(userRepository.findById(1L)).thenReturn(Optional.of(proposer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(betRepository.save(any(Bet.class))).thenAnswer(inv -> inv.getArgument(0));

        creator = new AutoTestBetCreator(betRepository, roomRepository, userRepository, gameRepository);
        ReflectionTestUtils.setField(creator, "roomId", 1L);
        ReflectionTestUtils.setField(creator, "proposerId", 1L);
        ReflectionTestUtils.setField(creator, "receiverId", 2L);
        ReflectionTestUtils.setField(creator, "everyN", 5);
    }

    @Test
    void N타석마다_한_번만_만든다() {
        for (int i = 0; i < 12; i++) creator.onAtBatStarted(event("타자" + i));

        // 5, 10번째에만 — 매 타석 만들면 경기당 80건이 넘는다
        verify(betRepository, times(2)).save(any(Bet.class));
    }

    @Test
    void 만들자마자_성립시킨다() {
        for (int i = 0; i < 5; i++) creator.onAtBatStarted(event("박찬호"));

        Bet bet = saved().get(0);
        assertThat(bet.getType()).isEqualTo(BetType.AT_BAT);
        assertThat(bet.getStatus())
                .as("콜을 기다릴 사람이 없으므로 즉시 성립시킨다")
                .isEqualTo(BetStatus.ACCEPTED);
        assertThat(bet.getReceiver()).isNotNull();
    }

    @Test
    void 지금_타석에_건다() {
        for (int i = 0; i < 5; i++) creator.onAtBatStarted(event("양의지"));

        assertThat(saved().get(0).matchesAtBat(7, HalfInning.BOTTOM, "양의지")).isTrue();
    }

    @Test
    void 아웃과_세이프를_번갈아_걸어_양쪽_결과를_모두_검증한다() {
        for (int i = 0; i < 20; i++) creator.onAtBatStarted(event("타자" + i));

        assertThat(saved()).extracting(Bet::getBetOnAtBatResult)
                .containsExactly(AtBatResult.OUT, AtBatResult.SAFE,
                                 AtBatResult.OUT, AtBatResult.SAFE);
    }

    @Test
    void 내용에_자동검증_표시를_남긴다() {
        for (int i = 0; i < 5; i++) creator.onAtBatStarted(event("박찬호"));

        assertThat(saved().get(0).getContent())
                .as("실제 유저 배팅과 눈으로 구분돼야 한다")
                .startsWith("[자동검증]");
    }

    @Test
    void 방이나_계정을_찾지_못하면_조용히_건너뛴다() {
        RoomRepository empty = mock(RoomRepository.class);
        when(empty.findById(any())).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(creator, "roomRepository", empty);

        for (int i = 0; i < 5; i++) creator.onAtBatStarted(event("박찬호"));

        verify(betRepository, never()).save(any());
    }

    /** 폴러 스레드에서 동기 호출된다 — 검증용 코드가 실제 감지를 멈추면 본말전도다 */
    @Test
    void 생성_중_예외가_나도_폴러로_전파되지_않는다() {
        when(betRepository.save(any(Bet.class))).thenThrow(new RuntimeException("DB down"));

        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) creator.onAtBatStarted(event("박찬호"));
        }).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ 헬퍼

    private List<Bet> saved() {
        ArgumentCaptor<Bet> c = ArgumentCaptor.forClass(Bet.class);
        verify(betRepository, atLeastOnce()).save(c.capture());
        return c.getAllValues();
    }

    private AtBatStartedEvent event(String batter) {
        return new AtBatStartedEvent(700L, "20260916_NC_두산", 7, HalfInning.BOTTOM, batter, "투수", false);
    }

    private <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
