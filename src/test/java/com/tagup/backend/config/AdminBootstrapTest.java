package com.tagup.backend.config;

import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 첫 관리자 지정 — 운영 DB에 직접 UPDATE를 치지 않으려는 장치.
 *
 * <p>Firebase 커스텀 클레임을 쓰지 않는 이유는 권한 변경이 토큰 갱신(최대 1시간) 후에야
 * 반영되고, 클레임을 심는 스크립트를 따로 돌려야 하기 때문이다.
 */
class AdminBootstrapTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    @Nested
    @DisplayName("이메일 설정값 파싱")
    class Emails {

        @Test
        void 쉼표로_나누고_공백과_대소문자를_정리한다() {
            assertThat(new AdminEmails(" A@b.com , c@d.com ,, ").normalized())
                    .containsExactly("a@b.com", "c@d.com");
        }

        @Test
        void 비어_있으면_아무것도_반환하지_않는다() {
            assertThat(new AdminEmails("").normalized()).isEmpty();
            assertThat(new AdminEmails(null).normalized()).isEmpty();
        }

        @Test
        void 중복은_한_번만_본다() {
            assertThat(new AdminEmails("a@b.com,A@B.COM").normalized()).containsExactly("a@b.com");
        }
    }

    @Nested
    @DisplayName("기동 시 승격")
    class Promotion {

        @Test
        void 설정된_이메일을_관리자로_올린다() {
            User user = user("owner@tagup.app");
            when(userRepository.findByEmailIgnoreCase("owner@tagup.app")).thenReturn(Optional.of(user));

            run("owner@tagup.app");

            assertThat(user.isAdmin()).isTrue();
            verify(userRepository).save(user);
        }

        @Test
        void 이미_관리자면_다시_저장하지_않는다() {
            User user = user("owner@tagup.app");
            user.promoteToAdmin();
            when(userRepository.findByEmailIgnoreCase("owner@tagup.app")).thenReturn(Optional.of(user));

            run("owner@tagup.app");

            verify(userRepository, never()).save(any());
        }

        @Test
        void 아직_가입하지_않았으면_그냥_넘어간다() {
            when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

            run("nobody@tagup.app");

            verify(userRepository, never()).save(any());
        }

        @Test
        void 설정이_비어_있으면_DB를_건드리지_않는다() {
            run("");

            verifyNoInteractions(userRepository);
        }

        private void run(String config) {
            new AdminBootstrap(userRepository, new AdminEmails(config)).run(null);
        }
    }

    private User user(String email) {
        return User.builder().firebaseUid("uid").email(email).nickname("오너").build();
    }
}
