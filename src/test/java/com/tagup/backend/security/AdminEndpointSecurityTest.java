package com.tagup.backend.security;

import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 관리자 API 접근 통제.
 *
 * <p>{@code /api/v1/admin/**} 은 2026-09-30까지 {@code permitAll} 이었다. 실제로 노출되지는
 * 않았는데, 유일한 관리자 컨트롤러가 {@code @Profile("local")} 이라 터널·프로덕션에서는 빈이
 * 만들어지지 않았기 때문이다. 즉 <b>설정은 열려 있었고 우연히 막혀 있었다.</b>
 * 관리자 엔드포인트가 하나라도 늘면 그대로 뚫리므로 규칙을 테스트로 고정한다.
 *
 * <p>local 프로파일은 Firebase가 초기화되지 않아 Authorization 헤더 값을 uid로 그대로 쓴다.
 * 실제 토큰 없이 특정 유저로 요청을 보낼 수 있어 권한 분기를 검증하기에 적합하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminEndpointSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;

    private User normalUser;

    @BeforeEach
    void setUp() {
        normalUser = userRepository.save(User.builder()
                .firebaseUid("uid-normal-" + System.nanoTime())
                .email("normal-" + System.nanoTime() + "@tagup.test")
                .nickname("일반유저")
                .build());
    }

    @Test
    @DisplayName("토큰 없이 크롤링 API를 호출할 수 없다")
    void crawlRequiresAuth() throws Exception {
        int status = mockMvc.perform(post("/api/v1/admin/games/crawl"))
                .andReturn().getResponse().getStatus();

        assertThat(status).as("익명 요청은 거부돼야 한다").isIn(401, 403);
    }

    @Test
    @DisplayName("일반 유저는 크롤링 API를 호출할 수 없다")
    void crawlRequiresAdmin() throws Exception {
        int status = mockMvc.perform(post("/api/v1/admin/games/crawl")
                        .header("Authorization", "Bearer " + normalUser.getFirebaseUid()))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }

    @Test
    @DisplayName("일반 유저는 남을 차단할 수 없다")
    void blockRequiresAdmin() throws Exception {
        int status = mockMvc.perform(post("/api/v1/admin/users/{id}/block", normalUser.getId())
                        .header("Authorization", "Bearer " + normalUser.getFirebaseUid()))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }

    @Test
    @DisplayName("차단된 계정은 일반 API도 쓸 수 없다")
    void blockedUserIsRejected() throws Exception {
        normalUser.block();
        userRepository.save(normalUser);

        int status = mockMvc.perform(post("/api/v1/rooms")
                        .header("Authorization", "Bearer " + normalUser.getFirebaseUid())
                        .contentType("application/json")
                        .content("{\"name\":\"테스트방\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }
}
