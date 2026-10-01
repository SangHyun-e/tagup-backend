package com.tagup.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>설정 한 줄이 빠져서 인증이 뚫리는 걸 막는 테스트다.</b>
 *
 * <p>{@code FirebaseTokenFilter}에는 Firebase 미초기화 시 토큰 검증을 건너뛰고 헤더 값을 그대로
 * uid로 쓰는 로컬 개발 경로가 있다. {@code tagup.security.require-firebase=true}면 기동 자체가
 * 막혀 이 경로를 탈 수 없다.
 *
 * <p>2026-09-30까지 이 설정이 <b>local-tunnel에만 있고 prod에는 없었다.</b> 그 상태로 배포하면
 * Firebase 키 환경변수가 잘못 들어가도 서버는 조용히 뜨고, 남의 uid 문자열만 알면 그 사람으로
 * 인증된다. 프로파일별 설정은 사람 눈으로 놓치기 쉬우므로 테스트로 고정한다.
 */
class ProdSecurityConfigTest {

    private static final String PROFILE_KEY = "spring.config.activate.on-profile";

    @Test
    @DisplayName("prod 프로파일은 Firebase 인증을 강제한다")
    void prodRequiresFirebase() {
        PropertySource<?> prod = documentFor("prod");

        assertThat(prod.getProperty("tagup.security.require-firebase"))
                .as("prod에 require-firebase가 없으면 인증 우회 경로가 살아 있다")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("터널 운영 프로파일도 Firebase 인증을 강제한다")
    void tunnelRequiresFirebase() {
        assertThat(documentFor("local-tunnel").getProperty("tagup.security.require-firebase"))
                .isEqualTo(true);
    }

    /** application.yml 의 여러 문서(---) 중 해당 프로파일 문서를 찾는다 */
    private PropertySource<?> documentFor(String profile) {
        List<PropertySource<?>> documents = load();
        return documents.stream()
                .filter(ps -> profile.equals(ps.getProperty(PROFILE_KEY)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(profile + " 프로파일 문서를 찾을 수 없다"));
    }

    private List<PropertySource<?>> load() {
        try {
            return new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"));
        } catch (Exception e) {
            throw new IllegalStateException("application.yml 로드 실패", e);
        }
    }
}
