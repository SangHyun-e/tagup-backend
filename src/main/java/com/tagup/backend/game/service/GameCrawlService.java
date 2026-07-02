package com.tagup.backend.game.service;

import com.tagup.backend.game.crawler.CrawledGame;
import com.tagup.backend.game.crawler.KboGameCrawler;
import com.tagup.backend.game.entity.Game;
import com.tagup.backend.game.entity.GameStatus;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.team.entity.Team;
import com.tagup.backend.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameCrawlService {

    private final KboGameCrawler kboGameCrawler;
    private final GameRepository gameRepository;
    private final TeamRepository teamRepository;

    /** 특정 날짜 크롤 (실시간 결과 업데이트용) */
    @Transactional
    public void crawlAndSave(LocalDate date) {
        saveGames(kboGameCrawler.crawlByDate(date), "date=" + date);
    }

    /** 월간 크롤 (이번 달 전체 일정 수집용) */
    @Transactional
    public void crawlAndSaveMonth(int year, int month) {
        saveGames(kboGameCrawler.crawlByMonth(year, month), String.format("%d-%02d", year, month));
    }

    private void saveGames(List<CrawledGame> crawled, String label) {
        if (crawled.isEmpty()) {
            log.info("크롤링 결과 없음 [{}]", label);
            return;
        }

        Map<String, Team> teamMap = teamRepository.findAll().stream()
                .collect(Collectors.toMap(Team::getShortName, Function.identity()));

        int saved = 0, updated = 0;
        for (CrawledGame cg : crawled) {
            Team homeTeam = teamMap.get(cg.homeTeamShortName());
            Team awayTeam = teamMap.get(cg.awayTeamShortName());

            if (homeTeam == null || awayTeam == null) {
                log.warn("팀 매핑 실패: home={}, away={}", cg.homeTeamShortName(), cg.awayTeamShortName());
                continue;
            }

            Optional<Game> existing = gameRepository.findByKboGameId(cg.kboGameId());
            if (existing.isPresent()) {
                existing.get().updateResult(cg.status(), cg.homeScore(), cg.awayScore());
                updated++;
            } else {
                gameRepository.save(Game.builder()
                        .kboGameId(cg.kboGameId())
                        .gameDate(cg.gameDate())
                        .gameTime(cg.gameTime())
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .status(cg.status() != null ? cg.status() : GameStatus.SCHEDULED)
                        .homeScore(cg.homeScore())
                        .awayScore(cg.awayScore())
                        .stadium(cg.stadium())
                        .build());
                saved++;
            }
        }
        log.info("경기 저장 완료 [{}]: 신규={}, 업데이트={}", label, saved, updated);
    }
}
