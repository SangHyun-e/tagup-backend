package com.tagup.backend.safety.service;

import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.safety.dto.BlockedUserResponse;
import com.tagup.backend.safety.entity.UserBlock;
import com.tagup.backend.safety.repository.UserBlockRepository;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자끼리의 차단.
 *
 * <p>운영자가 거는 계정 정지({@code UserStatus.BLOCKED})와 다르다. 차단당한 사람은 서비스를
 * 계속 쓰지만, <b>차단한 사람 눈에만</b> 보이지 않는다. 앱이 차단 목록을 받아 메시지를 가린다.
 *
 * <p>차단 요청은 <b>여러 번 눌러도 같은 결과</b>다. 이미 차단한 사람을 또 차단해도 오류를 내지
 * 않는다 — 신고 화면에서 "차단하기"를 두 번 누르는 건 흔한 일이고, 그때 에러를 띄울 이유가 없다.
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    @Transactional
    public void block(User blocker, Long blockedUserId) {
        if (blocker.getId().equals(blockedUserId)) {
            throw new CustomException(ErrorCode.CANNOT_BLOCK_SELF);
        }

        User blocked = userRepository.findById(blockedUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (userBlockRepository.existsByBlockerAndBlocked(blocker, blocked)) return;

        userBlockRepository.save(UserBlock.builder().blocker(blocker).blocked(blocked).build());
    }

    @Transactional
    public void unblock(User blocker, Long blockedUserId) {
        User blocked = userRepository.findById(blockedUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        userBlockRepository.findByBlockerAndBlocked(blocker, blocked)
                .ifPresent(userBlockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> myBlocks(User blocker) {
        return userBlockRepository.findAllByBlockerWithUser(blocker).stream()
                .map(BlockedUserResponse::from)
                .toList();
    }
}
