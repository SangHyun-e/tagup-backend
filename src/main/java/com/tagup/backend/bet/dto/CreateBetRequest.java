package com.tagup.backend.bet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBetRequest(
        @NotNull(message = "수신자 ID를 입력해주세요.")
        Long receiverId,

        @NotNull(message = "경기 ID를 입력해주세요.")
        Long gameId,

        @NotBlank(message = "내기 내용을 입력해주세요.")
        @Size(max = 100, message = "내기 내용은 100자 이하여야 합니다.")
        String content,

        @NotNull(message = "응원할 팀 ID를 입력해주세요.")
        Long betOnTeamId
) {}
