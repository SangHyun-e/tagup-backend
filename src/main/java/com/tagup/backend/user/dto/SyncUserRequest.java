package com.tagup.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SyncUserRequest(
        @NotBlank(message = "닉네임을 입력해주세요.")
        @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
        String nickname,

        Long teamId  // 선택
) {}
