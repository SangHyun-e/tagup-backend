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

    // 매일 오전 7시: 당일 경기 일정 초기 수집
    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
    public void crawlTodaySchedule() {
        log.info("[스케줄러] 오늘 경기 일정 크롤링 시작");
        gameCrawlService.crawlAndSave(LocalDate.now());
    }

    // 경기 시즌 중 30분마다 실시간 경기 결과 업데이트 (오후 1시~11시)
    @Scheduled(cron = "0 */30 13-23 * * *", zone = "Asia/Seoul")
    public void updateGameResults() {
        log.info("[스케줄러] 경기 결과 업데이트 크롤링");
        gameCrawlService.crawlAndSave(LocalDate.now());
    }

    // 내일 경기 선행 수집 (매일 오전 8시)
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void crawlTomorrowSchedule() {
        log.info("[스케줄러] 내일 경기 일정 크롤링 시작");
        gameCrawlService.crawlAndSave(LocalDate.now().plusDays(1));
    }
}
