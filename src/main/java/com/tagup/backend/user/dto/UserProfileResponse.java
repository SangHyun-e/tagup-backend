package com.tagup.backend.user.dto;

import com.tagup.backend.team.dto.TeamResponse;
import com.tagup.backend.user.entity.User;

public record UserProfileResponse(
        Long id,
        String email,
        String nickname,
        TeamResponse favoriteTeam
) {
    public static UserProfileResponse from(User user) {
        TeamResponse team = user.getFavoriteTeam() != null
                ? TeamResponse.from(user.getFavoriteTeam())
                : null;
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getNickname(), team);
    }
}
