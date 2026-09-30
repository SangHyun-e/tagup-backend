package com.tagup.backend.game.live;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 그날 라이브 수집이 어땠는지 <b>한 줄로</b> 남긴다.
 *
 * <p>여태 결과 확인은 로그를 날짜별로 grep해 세는 식이었다. 사람이 아침에 한 줄만 보고
 * "어제 제대로 돌았나"를 판단할 수 있어야 한다. 특히 <b>노트북 절전</b>은 조용히 하루를
 * 통째로 날리므로, 폴링 공백이 몇 번 있었는지가 감지율만큼 중요하다.
 *
 * <p>집계는 메모리에만 둔다. 재시작하면 그날 수치는 사라진다 — 원본은 로그에 남아 있고,
 * 이 줄은 어디까지나 빠른 확인용이다.
 */
@Slf4j
@Component
public class LiveDailyReport {

    private final Clock clock;

    private LocalDate day;
    private final AtomicInteger out = new AtomicInteger();
    private final AtomicInteger safe = new AtomicInteger();
    private final AtomicInteger unknown = new AtomicInteger();
    private final AtomicInteger finalAtBats = new AtomicInteger();
    private final AtomicInteger staleSnapshots = new AtomicInteger();
    private final AtomicInteger pollGaps = new AtomicInteger();
    private final AtomicLong longestGapSeconds = new AtomicLong();
    private final AtomicInteger relayMessages = new AtomicInteger();

    public LiveDailyReport(Clock clock) {
        this.clock = clock;
        this.day = LocalDate.now(clock);
    }

    @EventListener
    public void onAtBat(AtBatDetectedEvent event) {
        rollOver();
        switch (event.atBat().result()) {
            case OUT -> out.incrementAndGet();
            case SAFE -> safe.incrementAndGet();
            case UNKNOWN -> unknown.incrementAndGet();
        }
    }

    public void recordFinalAtBat() {
        rollOver();
        finalAtBats.incrementAndGet();
    }

    public void recordStaleSnapshot() {
        rollOver();
        staleSnapshots.incrementAndGet();
    }

    public void recordPollGap(long gapSeconds) {
        rollOver();
        pollGaps.incrementAndGet();
        longestGapSeconds.accumulateAndGet(gapSeconds, Math::max);
    }

    public void recordRelayMessage() {
        rollOver();
        relayMessages.incrementAndGet();
    }

    /** 경기가 모두 끝난 뒤 하루치를 남긴다 */
    @Scheduled(cron = "${tagup.live.daily-report-cron:0 55 23 * * *}", zone = "Asia/Seoul")
    public void logDailySummary() {
        log.info("{}", summaryLine());
        reset(LocalDate.now(clock));
    }

    /** 사람이 읽는 한 줄. 테스트와 로그가 같은 문장을 본다 */
    public String summaryLine() {
        int total = out.get() + safe.get() + unknown.get();
        if (total == 0) {
            return "[일간 요약] %s — 감지된 타석 없음 (경기가 없었거나 수집이 멈춰 있었다)".formatted(day);
        }
        double settleable = 100.0 * (total - unknown.get()) / total;
        return ("[일간 요약] %s — 타석 %d건 (아웃 %d / 세이프 %d / 판정불가 %d · 정산가능 %.1f%%), "
                + "경기 종료 타석 %d건, 옛 스냅샷 무시 %d건, 폴링 공백 %d회%s, 중계 %d건")
                .formatted(day, total, out.get(), safe.get(), unknown.get(), settleable,
                        finalAtBats.get(), staleSnapshots.get(), pollGaps.get(),
                        pollGaps.get() > 0 ? "(최장 %d초)".formatted(longestGapSeconds.get()) : "",
                        relayMessages.get());
    }

    /** 날짜가 바뀌었는데 요약이 남지 않았다면(재시작·경기 없는 날) 조용히 넘긴다 */
    private synchronized void rollOver() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) reset(today);
    }

    private synchronized void reset(LocalDate today) {
        day = today;
        out.set(0);
        safe.set(0);
        unknown.set(0);
        finalAtBats.set(0);
        staleSnapshots.set(0);
        pollGaps.set(0);
        longestGapSeconds.set(0);
        relayMessages.set(0);
    }
}
