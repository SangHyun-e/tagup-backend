package com.tagup.backend.game.live;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 하루치 수집 상태를 한 줄로 요약한다.
 *
 * <p>사람이 아침에 이 줄만 보고 "어제 제대로 돌았나"를 판단한다. 그래서 문장 자체를 검증한다.
 */
class LiveDailyReportTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), SEOUL);
    private final LiveDailyReport report = new LiveDailyReport(clock);

    @Test
    @DisplayName("타석·정산가능률·공백을 한 줄에 담는다")
    void summary() {
        detect(AtBatResult.OUT, 3);
        detect(AtBatResult.SAFE, 1);
        detect(AtBatResult.UNKNOWN, 1);
        report.recordFinalAtBat();
        report.recordStaleSnapshot();
        report.recordPollGap(612);
        report.recordPollGap(90);
        report.recordRelayMessage();

        assertThat(report.summaryLine())
                .contains("2026-09-29")
                .contains("타석 5건")
                .contains("아웃 3 / 세이프 1 / 판정불가 1")
                .contains("정산가능 80.0%")
                .contains("경기 종료 타석 1건")
                .contains("옛 스냅샷 무시 1건")
                .contains("폴링 공백 2회(최장 612초)")
                .contains("중계 1건");
    }

    @Test
    @DisplayName("경기가 없었거나 수집이 멈춘 날은 그렇게 말한다")
    void emptyDay() {
        assertThat(report.summaryLine()).contains("감지된 타석 없음");
    }

    @Test
    @DisplayName("요약을 남기면 다음 날 수치가 섞이지 않는다")
    void resetAfterSummary() {
        detect(AtBatResult.OUT, 2);
        report.logDailySummary();

        assertThat(report.summaryLine()).contains("감지된 타석 없음");
    }

    private void detect(AtBatResult result, int times) {
        for (int i = 0; i < times; i++) {
            report.onAtBat(new AtBatDetectedEvent(1L, "20260929_KT_KIA",
                    new AtBatEvent("타자", "투수", 5, HalfInning.TOP, result, 0, false)));
        }
    }
}
