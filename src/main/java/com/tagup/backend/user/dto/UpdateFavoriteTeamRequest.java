package com.tagup.backend.user.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateFavoriteTeamRequest(
        @NotNull(message = "팀 ID는 필수입니다.")
        Long teamId
) {}
