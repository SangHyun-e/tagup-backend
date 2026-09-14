package com.tagup.backend.room.dto;

import com.tagup.backend.room.entity.RoomMember;

import java.time.LocalDateTime;

public record RoomMemberResponse(
        Long id,
        String nickname,
        String favoriteTeamName,
        LocalDateTime joinedAt
) {
    public static RoomMemberResponse from(RoomMember rm) {
        return new RoomMemberResponse(
                rm.getUser().getId(),
                rm.getUser().getNickname(),
                rm.getUser().getFavoriteTeam() != null ? rm.getUser().getFavoriteTeam().getName() : null,
                rm.getJoinedAt()
        );
    }
}
