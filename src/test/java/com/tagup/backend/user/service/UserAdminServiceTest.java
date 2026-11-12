package com.tagup.backend.user.service;

import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.entity.UserStatus;
import com.tagup.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 운영자 차단/해제 */
class UserAdminServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserAdminService service = new UserAdminService(userRepository);

    private final User admin = user(1L, "admin@tagup.app");
    private final User target = user(2L, "user@tagup.app");

    @Test
    @DisplayName("차단하면 상태가 BLOCKED 가 된다")
    void block() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        service.block(2L, admin);

        assertThat(target.getStatus()).isEqualTo(UserStatus.BLOCKED);
        assertThat(target.canUseService()).isFalse();
    }

    @Test
    @DisplayName("해제하면 다시 쓸 수 있다")
    void unblock() {
        target.block();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        service.unblock(2L, admin);

        assertThat(target.canUseService()).isTrue();
    }

    @Test
    @DisplayName("자기 자신은 차단할 수 없다 — 관리자가 스스로 잠기면 복구 수단이 없다")
    void cannotBlockSelf() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.block(1L, admin))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_BLOCK_SELF);
        assertThat(admin.canUseService()).isTrue();
    }

    @Test
    @DisplayName("없는 유저면 404")
    void notFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.block(99L, admin))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    private User user(Long id, String email) {
        User user = User.builder().firebaseUid("uid-" + id).email(email).nickname("닉").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
