package com.tagup.backend.user.dto;

public record AuthResponse(
        String accessToken,
        Long userId,
        String email,
        String nickname
) {
}
