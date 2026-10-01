package com.tagup.backend.game.live;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 진행 중인 경기의 지금 상태를 보관한다.
 *
 * <p>핵심은 <b>오래된 값을 내주지 않는 것</b>이다. 절전으로 폴링이 멈추면 마지막 스냅샷이 그대로
 * 남는데, 그걸 "지금 점수"로 보여주면 멈춘 경기를 생중계처럼 그리게 된다.
 * (2026-09-18~29 12일 중 절전 없이 온전히 수집한 날은 이틀뿐이었다)
 */
class LiveScoreboardRegistryTest {

    private static final String GAME = "20261001_삼성_두산";
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));
    private final LiveScoreboardRegistry registry = newRegistry(60_000L);

    @Test
    @DisplayName("방금 받은 스냅샷은 그대로 읽힌다")
    void freshSnapshot() {
        registry.update(GAME, live(7, HalfInning.TOP, 3, 5, 2, 7, 9, 0));

        LiveScoreboard board = registry.find(GAME).orElseThrow();
        assertThat(board.inning()).isEqualTo(7);
        assertThat(board.awayScore()).isEqualTo(3);
        assertThat(board.homeScore()).isEqualTo(5);
        assertThat(board.out()).isEqualTo(2);
        assertThat(board.batter()).isEqualTo("원정타자");
    }

    @Test
    @DisplayName("주자는 타순 번호로 오고, 0이면 비어 있는 루다")
    void bases() {
        registry.update(GAME, live(7, HalfInning.TOP, 3, 5, 2, 7, 9, 0));

        LiveScoreboard board = registry.find(GAME).orElseThrow();
        assertThat(board.firstBase()).isTrue();
        assertThat(board.secondBase()).isTrue();
        assertThat(board.thirdBase()).isFalse();
    }

    @Test
    @DisplayName("폴링이 멈춰 값이 오래되면 없는 것으로 친다")
    void staleIsHidden() {
        registry.update(GAME, live(7, HalfInning.TOP, 3, 5, 2, 0, 0, 0));

        clock.advanceSeconds(59);
        assertThat(registry.find(GAME)).isPresent();

        clock.advanceSeconds(2);   // 61초 경과
        assertThat(registry.find(GAME)).isEmpty();
    }

    @Test
    @DisplayName("폴링이 돌아오면 다시 읽힌다")
    void recoversAfterGap() {
        registry.update(GAME, live(7, HalfInning.TOP, 3, 5, 2, 0, 0, 0));
        clock.advanceSeconds(600);
        assertThat(registry.find(GAME)).isEmpty();

        registry.update(GAME, live(9, HalfInning.BOTTOM, 3, 7, 1, 0, 0, 0));
        assertThat(registry.find(GAME)).get()
                .extracting(LiveScoreboard::inning, LiveScoreboard::homeScore)
                .containsExactly(9, 7);
    }

    @Test
    @DisplayName("경기가 끝나면 상태를 지운다")
    void finishedGameIsRemoved() {
        registry.update(GAME, live(9, HalfInning.BOTTOM, 3, 5, 3, 0, 0, 0));

        registry.update(GAME, new LiveGameSnapshot(LiveGameState.FINISHED, 9, HalfInning.BOTTOM,
                3, 5, 0, 0, 3, "a", "b", 0, 0, 0));

        assertThat(registry.find(GAME)).isEmpty();
    }

    @Test
    @DisplayName("모르는 경기는 비어 있다")
    void unknownGame() {
        assertThat(registry.find("없는경기")).isEmpty();
    }

    private LiveScoreboardRegistry newRegistry(long ttlMs) {
        LiveScoreboardRegistry r = new LiveScoreboardRegistry(clock);
        ReflectionTestUtils.setField(r, "ttlMs", ttlMs);
        return r;
    }

    private LiveGameSnapshot live(int inning, HalfInning half, int away, int home, int out,
                                  int r1, int r2, int r3) {
        return new LiveGameSnapshot(LiveGameState.IN_PROGRESS, inning, half, away, home,
                1, 2, out, "원정타자", "홈선수", r1, r2, r3);
    }

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
