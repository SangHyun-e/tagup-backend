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

    @Transactional
    public void crawlAndSave(LocalDate date) {
        List<CrawledGame> crawled = kboGameCrawler.crawlByDate(date);
        if (crawled.isEmpty()) {
            log.info("크롤링 결과 없음 date={}", date);
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
        log.info("경기 저장 완료 date={}: 신규={}, 업데이트={}", date, saved, updated);
    }
}
