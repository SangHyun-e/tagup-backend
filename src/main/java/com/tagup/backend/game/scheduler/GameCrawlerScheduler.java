package com.tagup.backend.game.scheduler;

import com.tagup.backend.game.service.GameCrawlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameCrawlerScheduler {

    private final GameCrawlService gameCrawlService;

    // 매일 오전 6시: 이번 달 전체 일정 수집 (월말이면 다음 달도)
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void crawlMonthlySchedule() {
        LocalDate today = LocalDate.now();
        log.info("[스케줄러] 월간 경기 일정 크롤링 시작 {}-{}", today.getYear(), today.getMonthValue());
        gameCrawlService.crawlAndSaveMonth(today.getYear(), today.getMonthValue());

        // 월말 25일 이후: 다음 달 일정도 선행 수집
        if (today.getDayOfMonth() >= 25) {
            LocalDate nextMonth = today.plusMonths(1);
            log.info("[스케줄러] 다음 달 경기 일정 선행 크롤링 {}-{}", nextMonth.getYear(), nextMonth.getMonthValue());
            gameCrawlService.crawlAndSaveMonth(nextMonth.getYear(), nextMonth.getMonthValue());
        }
    }

    // 경기 시즌 중 30분마다: 오늘 경기 실시간 결과 업데이트 (오후 1시~11시)
    @Scheduled(cron = "0 */30 13-23 * * *", zone = "Asia/Seoul")
    public void updateTodayResults() {
        log.info("[스케줄러] 오늘 경기 결과 업데이트");
        gameCrawlService.crawlAndSave(LocalDate.now());
    }
}
