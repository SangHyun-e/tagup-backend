package com.tagup.backend.security;

import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 인증 필터 — <b>차단·탈퇴 계정을 여기서 끊는다.</b>
 *
 * <p>Firebase에서 계정을 비활성화해도 이미 발급된 ID 토큰은 최대 1시간 더 통과한다
 * ({@code verifyIdToken}은 서명과 만료만 본다). 그래서 실제 차단은 우리 DB 상태로 한다.
 *
 * <p>Firebase 미초기화 상태(로컬 모드)에서는 토큰 문자열을 uid로 그대로 쓴다. 이 테스트도
 * 그 경로를 이용한다 — 프로덕션에서는 {@code tagup.security.require-firebase=true}로
 * 기동 자체가 막히므로 이 경로를 탈 수 없다.
 */
class FirebaseTokenFilterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FirebaseTokenFilter filter = new FirebaseTokenFilter(userRepository);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("정상 계정은 ROLE_USER 권한으로 인증된다")
    void activeUser() throws Exception {
        MockHttpServletResponse response = call(user("uid-1"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(authorities()).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("관리자는 ROLE_ADMIN 권한을 받는다")
    void adminUser() throws Exception {
        User admin = user("uid-2");
        admin.promoteToAdmin();

        call(admin);

        assertThat(authorities()).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("차단된 계정은 403으로 끊고 다음 필터로 넘기지 않는다")
    void blockedUser() throws Exception {
        User blocked = user("uid-3");
        blocked.block();

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = call(blocked, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("이용이 제한된 계정입니다.");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("탈퇴한 계정은 탈퇴라고 알려준다")
    void withdrawnUser() throws Exception {
        User withdrawn = user("uid-4");
        withdrawn.withdraw();

        MockHttpServletResponse response = call(withdrawn);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("탈퇴한 계정입니다.");
    }

    @Test
    @DisplayName("토큰이 없으면 인증 없이 통과시킨다 (공개 엔드포인트용)")
    void noToken() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("모르는 uid면 인증하지 않는다")
    void unknownUid() throws Exception {
        when(userRepository.findByFirebaseUid("없는uid")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer 없는uid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletResponse call(User user) throws Exception {
        return call(user, mock(FilterChain.class));
    }

    private MockHttpServletResponse call(User user, FilterChain chain) throws Exception {
        when(userRepository.findByFirebaseUid(user.getFirebaseUid())).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + user.getFirebaseUid());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);
        return response;
    }

    private List<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private User user(String uid) {
        return User.builder()
                .firebaseUid(uid)
                .email(uid + "@tagup.test")
                .nickname("테스터")
                .build();
    }
}
