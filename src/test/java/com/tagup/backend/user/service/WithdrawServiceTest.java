package com.tagup.backend.user.service;

import com.tagup.backend.bet.entity.Bet;
import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.notification.repository.UserDeviceRepository;
import com.tagup.backend.room.repository.RoomMemberRepository;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.entity.UserStatus;
import com.tagup.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 회원 탈퇴.
 *
 * <p>앱 안에서 계정을 지울 수 없으면 애플 심사에서 반려된다(5.1.1 v). 다만 행을 지우면 상대방의
 * 내기 기록이 깨지므로, 개인정보만 끊고 "탈퇴한 사용자"로 남긴다.
 */
class WithdrawServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoomMemberRepository roomMemberRepository = mock(RoomMemberRepository.class);
    private final BetRepository betRepository = mock(BetRepository.class);
    private final UserDeviceRepository userDeviceRepository = mock(UserDeviceRepository.class);

    private final WithdrawService service = new WithdrawService(
            userRepository, roomMemberRepository, betRepository, userDeviceRepository);

    @Test
    @DisplayName("탈퇴하면 상태가 WITHDRAWN 이 되고 더 이상 서비스를 쓸 수 없다")
    void withdrawBlocksService() {
        User user = user();

        service.withdraw(user);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.canUseService()).isFalse();
    }

    @Test
    @DisplayName("개인정보를 지운다 — 이메일·닉네임·Firebase 연결")
    void anonymizes() {
        User user = user();

        service.withdraw(user);

        assertThat(user.getEmail()).doesNotContain("real@tagup.app").endsWith("@tagup.invalid");
        assertThat(user.getNickname()).isEqualTo("탈퇴한 사용자");
        assertThat(user.getFirebaseUid()).isNull();
    }

    @Test
    @DisplayName("아직 아무도 받지 않은 내기는 취소한다")
    void cancelsPendingBets() {
        User user = user();
        Bet pending = mock(Bet.class);
        when(betRepository.findByProposerAndStatus(user, BetStatus.PENDING))
                .thenReturn(List.of(pending));

        service.withdraw(user);

        verify(pending).cancel();
    }

    @Test
    @DisplayName("더그아웃 멤버십과 기기 토큰을 지운다 — 탈퇴한 계정으로 푸시가 가면 안 된다")
    void removesMembershipsAndDevices() {
        User user = user();

        service.withdraw(user);

        verify(roomMemberRepository).deleteByUser(user);
        verify(userDeviceRepository).deleteByUser(user);
    }

    @Test
    @DisplayName("이미 성립된 내기는 건드리지 않는다 — 상대방에게 결과가 나와야 한다")
    void keepsAcceptedBets() {
        User user = user();

        service.withdraw(user);

        verify(betRepository, never()).findByProposerAndStatus(user, BetStatus.ACCEPTED);
        verify(betRepository, never()).delete(any());
    }

    private User user() {
        User user = User.builder()
                .firebaseUid("uid-1")
                .email("real@tagup.app")
                .nickname("상현")
                .build();
        ReflectionTestUtils.setField(user, "id", 7L);
        when(betRepository.findByProposerAndStatus(eq(user), any())).thenReturn(List.of());
        return user;
    }
}
