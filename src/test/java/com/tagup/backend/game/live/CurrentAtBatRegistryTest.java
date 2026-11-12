package com.tagup.backend.game.live;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 타석 배팅이 "지금 타석"을 어디서 읽는지 */
class CurrentAtBatRegistryTest {

    private final CurrentAtBatRegistry registry = new CurrentAtBatRegistry();
    private static final String GAME = "20260914_NC_두산";
    private static final Duration WINDOW = Duration.ofSeconds(30);

    @Test
    void 진행_중인_경기의_타자를_기록한다() {
        registry.update(GAME, live(3, HalfInning.BOTTOM, "잭로그", "박찬호"), Instant.now());

        assertThat(registry.find(GAME)).get()
                .extracting(CurrentAtBat::inning, CurrentAtBat::half, CurrentAtBat::batter)
                .containsExactly(3, HalfInning.BOTTOM, "박찬호");
    }

    @Test
    void 초에는_원정선수가_타자로_기록된다() {
        registry.update(GAME, live(1, HalfInning.TOP, "박민우", "곽빈"), Instant.now());

        assertThat(registry.find(GAME)).get()
                .extracting(CurrentAtBat::batter).isEqualTo("박민우");
    }

    @Test
    void 진행_중이_아니면_비운다() {
        registry.update(GAME, live(3, HalfInning.BOTTOM, "잭로그", "박찬호"), Instant.now());

        LiveGameSnapshot finished = new LiveGameSnapshot(LiveGameState.FINISHED, 9, HalfInning.BOTTOM,
                2, 9, 0, 0, 3, "잭로그", "박찬호", null, null, null);
        registry.update(GAME, finished, Instant.now());

        assertThat(registry.find(GAME))
                .as("끝난 경기에 타석 배팅이 걸리면 안 된다")
                .isEmpty();
    }

    @Test
    void 타자를_식별할_수_없으면_비운다() {
        LiveGameSnapshot noBatter = new LiveGameSnapshot(LiveGameState.IN_PROGRESS, 1, null,
                0, 0, 0, 0, 0, "", "", null, null, null);
        registry.update(GAME, noBatter, Instant.now());

        assertThat(registry.find(GAME)).isEmpty();
    }

    @Test
    void 다른_경기와_섞이지_않는다() {
        registry.update(GAME, live(3, HalfInning.BOTTOM, "잭로그", "박찬호"), Instant.now());
        registry.update("20260914_LG_삼성", live(5, HalfInning.TOP, "오지환", "최원태"), Instant.now());

        assertThat(registry.find(GAME)).get().extracting(CurrentAtBat::batter).isEqualTo("박찬호");
        assertThat(registry.find("20260914_LG_삼성")).get()
                .extracting(CurrentAtBat::batter).isEqualTo("오지환");
    }

    // ------------------------------------------------------------ 배팅 창

    @Test
    void 감지_직후에는_열려_있고_창을_넘기면_닫힌다() {
        Instant start = Instant.now();
        CurrentAtBat atBat = new CurrentAtBat(3, HalfInning.BOTTOM, "박찬호", start);

        assertThat(atBat.isOpen(start, WINDOW)).isTrue();
        assertThat(atBat.isOpen(start.plusSeconds(29), WINDOW)).isTrue();
        assertThat(atBat.isOpen(start.plusSeconds(30), WINDOW)).isTrue();
        assertThat(atBat.isOpen(start.plusSeconds(31), WINDOW)).isFalse();
    }

    private LiveGameSnapshot live(int inning, HalfInning half, String awayPlayer, String homePlayer) {
        return new LiveGameSnapshot(LiveGameState.IN_PROGRESS, inning, half,
                0, 0, 0, 0, 0, awayPlayer, homePlayer, null, null, null);
    }
}
