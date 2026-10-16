package com.tagup.backend.game.live;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

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

        assertThat(msg).isEqualTo("3회말 박찬호 타석 (투수 곽빈)");
        assertThat(msg).doesNotContain("교체");
    }

    @Test
    void 투수가_바뀌면_교체를_먼저_알린다() {
        String msg = LiveRelayMessage.atBatStarted(started(7, HalfInning.TOP, "박민우", "홍건희", true));

        assertThat(msg).startsWith("투수 교체 — 홍건희");
        assertThat(msg).contains("7회초 박민우 타석");
    }

    @Test
    void 투수를_모르면_투수_문구를_빼고_말한다() {
        String msg = LiveRelayMessage.atBatStarted(started(1, HalfInning.TOP, "박민우", "", false));

        assertThat(msg).isEqualTo("1회초 박민우 타석");
    }

    @Test
    void 아웃과_세이프만_말한다() {
        assertThat(LiveRelayMessage.atBatFinished(finished("박찬호", AtBatResult.OUT, 0, false)))
                .isEqualTo("박찬호 아웃");
        assertThat(LiveRelayMessage.atBatFinished(finished("박찬호", AtBatResult.SAFE, 0, false)))
                .isEqualTo("박찬호 세이프");
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
                .isEqualTo("오스틴 세이프 · 2점");
        assertThat(LiveRelayMessage.atBatFinished(finished("오스틴", AtBatResult.OUT, 0, true)))
                .isEqualTo("오스틴 아웃 · 이닝 종료");
    }

    @Test
    void 판정_불가도_숨기지_않고_알린다() {
        assertThat(LiveRelayMessage.atBatFinished(finished("손아섭", AtBatResult.UNKNOWN, 0, false)))
                .as("무효 처리될 배팅이 왜 무효인지 사용자가 알 수 있어야 한다")
                .isEqualTo("손아섭 결과 확인 불가");
    }

    private AtBatStartedEvent started(int inning, HalfInning half, String batter,
                                      String pitcher, boolean changed) {
        return new AtBatStartedEvent(1L, "20260915_NC_두산", inning, half, batter, pitcher, changed);
    }

    private AtBatEvent finished(String batter, AtBatResult result, int runs, boolean endedInning) {
        return new AtBatEvent(batter, "투수", 3, HalfInning.BOTTOM, result, runs, endedInning);
    }

    // ------------------------------------------------------------------
    // 앱이 그릴 수 있도록 함께 보내는 데이터.
    // 문자열만 보내던 시절에는 앱에서 중계 줄의 모양을 바꿀 수 없었다.

    @Test
    @DisplayName("타석 시작 데이터에 타자·투수·이닝이 담긴다")
    void startedData() {
        Map<String, Object> data = LiveRelayMessage.startedData(
                new AtBatStartedEvent(1L, "20261002_LG_두산", 7, HalfInning.TOP, "박민우", "홍건희", true));

        assertThat(data)
                .containsEntry("inning", 7)
                .containsEntry("half", "TOP")
                .containsEntry("batter", "박민우")
                .containsEntry("pitcher", "홍건희")
                .containsEntry("pitcherChanged", true);
    }

    @Test
    @DisplayName("타석 결과 데이터에 결과·득점·이닝 종료가 담긴다")
    void finishedData() {
        Map<String, Object> data = LiveRelayMessage.finishedData(
                new AtBatEvent("오스틴", "곽빈", 5, HalfInning.BOTTOM, AtBatResult.SAFE, 2, false));

        assertThat(data)
                .containsEntry("batter", "오스틴")
                .containsEntry("result", "SAFE")
                .containsEntry("runsScored", 2)
                .containsEntry("endedInning", false);
    }

    @Test
    @DisplayName("비어 있는 값은 아예 보내지 않는다 — 앱에서 빈 칸을 그리지 않도록")
    void blanksAreOmitted() {
        Map<String, Object> data = LiveRelayMessage.startedData(
                new AtBatStartedEvent(1L, "G", null, null, "박민우", "", false));

        assertThat(data).containsKey("batter");
        assertThat(data).doesNotContainKeys("inning", "half", "pitcher");
    }

    @Test
    @DisplayName("문구에는 이모지를 쓰지 않는다 — 기기마다 모양이 달라 디자인을 통제할 수 없다")
    void noEmojiInText() {
        String started = LiveRelayMessage.atBatStarted(
                new AtBatStartedEvent(1L, "G", 7, HalfInning.TOP, "박민우", "홍건희", true));
        String finished = LiveRelayMessage.atBatFinished(
                new AtBatEvent("오스틴", "곽빈", 5, HalfInning.BOTTOM, AtBatResult.OUT, 0, true));

        assertThat(started + finished).doesNotContainPattern("[\\p{So}\\p{Cn}]");
    }
}
