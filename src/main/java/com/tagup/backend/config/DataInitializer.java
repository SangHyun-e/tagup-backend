package com.tagup.backend.config;

import com.tagup.backend.team.entity.Team;
import com.tagup.backend.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final TeamRepository teamRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
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

    private Team createTeam(String name, String shortName, String logoUrl) {
        return Team.builder()
                .name(name)
                .shortName(shortName)
                .logoUrl(logoUrl)
                .build();
    }
}
