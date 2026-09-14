package com.tagup.backend.game.live;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 중계 문구.
 *
 * <p>핵심 제약: KBO는 <b>뜬공·땅볼·안타·볼넷을 구분해주지 않는다.</b>
 * 없는 정보를 있는 것처럼 쓰면 사용자가 오해하므로 아웃/세이프로만 말한다.
 */
class LiveRelayMessageTest {

    @Test
    void 타석_시작은_이닝과_타자와_투수를_알린다() {
        String msg = LiveRelayMessage.atBatStarted(started(3, HalfInning.BOTTOM, "박찬호", "곽빈", false));

        assertThat(msg).isEqualTo("⚾ 3회말 박찬호 타석 (투수 곽빈)");
        assertThat(msg).doesNotContain("교체");
    }

    @Test
    void 투수가_바뀌면_교체를_먼저_알린다() {
        String msg = LiveRelayMessage.atBatStarted(started(7, HalfInning.TOP, "박민우", "홍건희", true));

        assertThat(msg).startsWith("🔄 투수 교체 — 홍건희");
        assertThat(msg).contains("7회초 박민우 타석");
    }

    @Test
    void 투수를_모르면_투수_문구를_빼고_말한다() {
        String msg = LiveRelayMessage.atBatStarted(started(1, HalfInning.TOP, "박민우", "", false));

        assertThat(msg).isEqualTo("⚾ 1회초 박민우 타석");
    }

    @Test
    void 아웃과_세이프만_말한다() {
        assertThat(LiveRelayMessage.atBatFinished(finished("박찬호", AtBatResult.OUT, 0, false)))
                .isEqualTo("🔴 박찬호 아웃");
        assertThat(LiveRelayMessage.atBatFinished(finished("박찬호", AtBatResult.SAFE, 0, false)))
                .isEqualTo("🟢 박찬호 세이프");
    }

    @Test
    void 뜬공이나_안타_같은_표현은_쓰지_않는다() {
        for (AtBatResult r : AtBatResult.values()) {
            String msg = LiveRelayMessage.atBatFinished(finished("박찬호", r, 2, true));
            assertThat(msg)
                    .as("KBO 응답으로는 구분할 수 없는 정보다 — %s", r)
                    .doesNotContain("뜬공").doesNotContain("땅볼")
                    .doesNotContain("안타").doesNotContain("볼넷").doesNotContain("홈런");
        }
    }

    @Test
    void 득점과_이닝_종료를_덧붙인다() {
        assertThat(LiveRelayMessage.atBatFinished(finished("오스틴", AtBatResult.SAFE, 2, false)))
                .isEqualTo("🟢 오스틴 세이프 · 2점!");
        assertThat(LiveRelayMessage.atBatFinished(finished("오스틴", AtBatResult.OUT, 0, true)))
                .isEqualTo("🔴 오스틴 아웃 · 이닝 종료");
    }

    @Test
    void 판정_불가도_숨기지_않고_알린다() {
        assertThat(LiveRelayMessage.atBatFinished(finished("손아섭", AtBatResult.UNKNOWN, 0, false)))
                .as("무효 처리될 배팅이 왜 무효인지 사용자가 알 수 있어야 한다")
                .isEqualTo("❔ 손아섭 결과 확인 불가");
    }

    private AtBatStartedEvent started(int inning, HalfInning half, String batter,
                                      String pitcher, boolean changed) {
        return new AtBatStartedEvent(1L, "20260915_NC_두산", inning, half, batter, pitcher, changed);
    }

    private AtBatEvent finished(String batter, AtBatResult result, int runs, boolean endedInning) {
        return new AtBatEvent(batter, "투수", 3, HalfInning.BOTTOM, result, runs, endedInning);
    }
}
