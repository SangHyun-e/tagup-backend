package com.tagup.backend.safety.dto;

import com.tagup.backend.safety.entity.UserBlock;

import java.time.LocalDateTime;

/** 내가 차단한 사람 */
public record BlockedUserResponse(Long userId, String nickname, LocalDateTime blockedAt) {

    public static BlockedUserResponse from(UserBlock block) {
        return new BlockedUserResponse(
                block.getBlocked().getId(),
                block.getBlocked().getNickname(),
                block.getCreatedAt());
    }
}
