package com.tagup.backend.game.live;

import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.team.entity.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * KBO가 섞어 주는 옛 스냅샷 거름.
 *
 * <p>시나리오는 2026-09-16 삼성-두산 실제 로그(19:56~20:00)를 옮겼다. 5회말 정수빈 타석 중에
 * 5회초 2아웃(심재훈 타석) 스냅샷이 한 번 끼어들어, 정수빈 타석이 가짜로 "종료"되고
 * 이미 끝난 심재훈 타석이 한 번 더 감지됐다.
 */
class StaleSnapshotTest {

    private static final String GAME_ID = "20260916_삼성_두산";

    // 5회말 두산 공격: 홈 선수가 타자, 원정 선수가 투수
    private final LiveGameSnapshot bottom5Start = live(5, HalfInning.BOTTOM, 0, 1, 3, "페덱", "정수빈");
    private final LiveGameSnapshot staleTop5 = live(5, HalfInning.TOP, 2, 1, 3, "심재훈", "벤자민");
    private final LiveGameSnapshot bottom5OneOut = live(5, HalfInning.BOTTOM, 1, 1, 3, "페덱", "박찬호");

    @Nested
    @DisplayName("isBehind")
    class IsBehind {

        @Test
        void 같은_회_말에서_초로_돌아가면_과거다() {
            assertThat(staleTop5.isBehind(bottom5Start)).isTrue();
        }

        @Test
        void 앞선_회로_돌아가면_과거다() {
            assertThat(live(4, HalfInning.BOTTOM, 0, 1, 3, "a", "b").isBehind(bottom5Start)).isTrue();
        }

        @Test
        void 어느_팀이든_점수가_줄면_과거다() {
            assertThat(live(5, HalfInning.BOTTOM, 0, 1, 2, "a", "b").isBehind(bottom5Start)).isTrue();
        }

        @Test
        void 앞으로_가거나_같은_상태는_과거가_아니다() {
            assertThat(bottom5OneOut.isBehind(bottom5Start)).isFalse();
            assertThat(live(6, HalfInning.TOP, 0, 1, 3, "a", "b").isBehind(bottom5Start)).isFalse();
            assertThat(bottom5Start.isBehind(bottom5Start)).isFalse();
        }

        @Test
        void 아웃_카운트만_줄어든_것은_과거로_보지_않는다() {
            // 공수 교대 순간 필드가 따로 갱신될 수 있어 아웃은 판단에 쓰지 않는다
            assertThat(bottom5Start.isBehind(bottom5OneOut)).isFalse();
        }

        @Test
        void 진행_중이_아니면_비교하지_않는다() {
            LiveGameSnapshot finished = new LiveGameSnapshot(LiveGameState.FINISHED,
                    null, null, 0, 0, null, null, null, null, null, null, null, null);
            assertThat(finished.isBehind(bottom5Start)).isFalse();
        }
    }

    @Nested
    @DisplayName("폴러")
    class Poller {

        private final KboLiveClient client = mock(KboLiveClient.class);
        private final GameRepository games = mock(GameRepository.class);
        private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        private LiveGamePoller poller;

        @BeforeEach
        void setUp() {
            poller = new LiveGamePoller(client, games, publisher, new CurrentAtBatRegistry());
            when(games.findByGameDateAndStatusIn(any(), anyList())).thenReturn(List.of(game()));
        }

        @Test
        void 옛_스냅샷이_끼어도_타석은_한_번만_올바르게_감지된다() {
            feed(bottom5Start, staleTop5, bottom5OneOut);

            List<AtBatEvent> events = detected();
            assertThat(events).hasSize(1);
            assertThat(events.getFirst().batter()).isEqualTo("정수빈");
            assertThat(events.getFirst().result()).isEqualTo(AtBatResult.OUT);
            assertThat(events.getFirst().half()).isEqualTo(HalfInning.BOTTOM);
        }

        @Test
        void 옛_스냅샷이_여러_번_번갈아_섞여도_무시한다() {
            // 9/16 SSG-롯데 9회말: 약 1분 반 동안 옛 스냅샷과 현재 스냅샷이 번갈아 왔다
            feed(bottom5Start, staleTop5, bottom5Start, staleTop5, staleTop5, bottom5OneOut);

            assertThat(detected()).extracting(AtBatEvent::batter).containsExactly("정수빈");
        }

        @Test
        void 점수가_되돌아갔다_돌아와도_득점을_두_번_세지_않는다() {
            // 9/15 KIA-SSG 5회말 실제 로그: 3:6 → 3:3(옛 스냅샷) → 3:6 에서 고명준 3점이 두 번 세져
            // 경기 득점이 9점인데 12점으로 집계됐다. 9/15~17 득점 과다 3건이 모두 이 패턴이었다.
            LiveGameSnapshot before = live(5, HalfInning.BOTTOM, 2, 3, 3, "조상우", "고명준");
            LiveGameSnapshot homer = live(5, HalfInning.BOTTOM, 2, 3, 6, "조상우", "안재연");

            feed(before, homer, before, homer);

            assertThat(detected()).extracting(AtBatEvent::batter).containsExactly("고명준");
            assertThat(detected()).extracting(AtBatEvent::runsScored).containsExactly(3);
        }

        @Test
        void 과거_상태가_계속되면_KBO_정정으로_보고_받아들인다() {
            LiveGameSnapshot[] seq = new LiveGameSnapshot[21];
            seq[0] = bottom5Start;
            for (int i = 1; i <= 20; i++) seq[i] = staleTop5;
            feed(seq);

            assertThat(poller.latest(GAME_ID)).contains(staleTop5);
            // 받아들이는 순간에도 가짜 타석 종료를 만들지 않는다
            assertThat(detected()).isEmpty();
        }

        private void feed(LiveGameSnapshot... seq) {
            for (LiveGameSnapshot s : seq) {
                when(client.fetchSnapshots(any(LocalDate.class))).thenReturn(Map.of(GAME_ID, s));
                poller.poll();
            }
        }

        private List<AtBatEvent> detected() {
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(publisher, atLeast(0)).publishEvent(captor.capture());
            return captor.getAllValues().stream()
                    .filter(AtBatDetectedEvent.class::isInstance)
                    .map(e -> ((AtBatDetectedEvent) e).atBat())
                    .toList();
        }

        private Game game() {
            Team away = Team.builder().name("삼성 라이온즈").shortName("삼성").build();
            Team home = Team.builder().name("두산 베어스").shortName("두산").build();
            Game g = Game.builder()
                    .kboGameId(GAME_ID)
                    .gameDate(LocalDate.now()).gameTime(LocalTime.of(18, 30))
                    .awayTeam(away).homeTeam(home)
                    .status(GameStatus.IN_PROGRESS)
                    .build();
            ReflectionTestUtils.setField(g, "id", 1L);
            return g;
        }
    }

    private static LiveGameSnapshot live(int inning, HalfInning half, int out,
                                         int away, int home, String awayPlayer, String homePlayer) {
        return new LiveGameSnapshot(LiveGameState.IN_PROGRESS, inning, half, away, home,
                0, 0, out, awayPlayer, homePlayer, null, null, null);
    }
}
