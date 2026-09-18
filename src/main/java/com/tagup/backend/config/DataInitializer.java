package com.tagup.backend.config;

import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.game.service.GameCrawlService;
import com.tagup.backend.team.entity.Team;
import com.tagup.backend.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final TeamRepository teamRepository;
    private final GameRepository gameRepository;
    private final GameCrawlService gameCrawlService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initTeams();
        initGames();
    }

    private void initTeams() {
        if (teamRepository.count() > 0) return;

        List<Team> teams = List.of(
                createTeam("KIA 타이거즈", "KIA", "https://via.placeholder.com/48?text=KIA"),
                createTeam("삼성 라이온즈", "삼성", "https://via.placeholder.com/48?text=삼성"),
                createTeam("LG 트윈스", "LG", "https://via.placeholder.com/48?text=LG"),
                createTeam("두산 베어스", "두산", "https://via.placeholder.com/48?text=두산"),
                createTeam("KT 위즈", "KT", "https://via.placeholder.com/48?text=KT"),
                createTeam("SSG 랜더스", "SSG", "https://via.placeholder.com/48?text=SSG"),
                createTeam("롯데 자이언츠", "롯데", "https://via.placeholder.com/48?text=롯데"),
                createTeam("한화 이글스", "한화", "https://via.placeholder.com/48?text=한화"),
                createTeam("NC 다이노스", "NC", "https://via.placeholder.com/48?text=NC"),
                createTeam("키움 히어로즈", "키움", "https://via.placeholder.com/48?text=키움")
        );
        teamRepository.saveAll(teams);
        log.info("KBO 10개 구단 초기 데이터 삽입 완료");
    }

    private void initGames() {
        if (gameRepository.count() > 0) return;

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int currentMonth = today.getMonthValue();

        // KBO 정규시즌 시작(3월)부터 현재 달까지 전체 크롤
        log.info("서버 시작 시 KBO 시즌 전체 경기 일정 크롤링: {}.03 ~ {}.{}", year, year, currentMonth);
        for (int month = 3; month <= currentMonth; month++) {
            gameCrawlService.crawlAndSaveMonth(year, month);
        }
    }

    private Team createTeam(String name, String shortName, String logoUrl) {
        return Team.builder().name(name).shortName(shortName).logoUrl(logoUrl).build();
    }
}
