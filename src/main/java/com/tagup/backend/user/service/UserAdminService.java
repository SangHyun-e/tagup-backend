package com.tagup.backend.user.service;

import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자용 유저 차단/해제.
 *
 * <p>차단은 <b>우리 DB 상태</b>로 한다. Firebase 콘솔에서 계정을 비활성화하는 것만으로는
 * 이미 발급된 ID 토큰이 최대 1시간 더 통과하기 때문이다. 여기서 바꾼 상태는
 * {@code FirebaseTokenFilter}가 매 요청 조회하므로 <b>다음 요청부터 즉시</b> 적용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;

    @Transactional
    public void block(Long userId, User admin) {
        User target = find(userId);
        if (target.getId().equals(admin.getId())) {
            throw new CustomException(ErrorCode.CANNOT_BLOCK_SELF);
        }
        target.block();
        log.warn("[관리자] {}(id={}) 계정을 차단했습니다 — 처리자 {}",
                target.getEmail(), target.getId(), admin.getEmail());
    }

    @Transactional
    public void unblock(Long userId, User admin) {
        User target = find(userId);
        target.unblock();
        log.info("[관리자] {}(id={}) 계정 차단을 해제했습니다 — 처리자 {}",
                target.getEmail(), target.getId(), admin.getEmail());
    }

    private User find(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
