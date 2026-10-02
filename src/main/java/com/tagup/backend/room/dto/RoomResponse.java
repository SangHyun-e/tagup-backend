package com.tagup.backend.room.dto;

import com.tagup.backend.game.dto.GameResponse;
import com.tagup.backend.room.entity.Room;

import java.time.LocalDateTime;

public record RoomResponse(
        Long id,
        String tagCode,
        String chatKey,
        String name,
        String createdByNickname,
        boolean chatEnabled,
        /** 오늘 이 더그아웃이 같이 보는 경기. 정해지지 않았으면 null */
        GameResponse watchingGame,
        LocalDateTime createdAt
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getTagCode(),
                room.getChatKey(),
                room.getName(),
                room.getCreatedBy().getNickname(),
                room.isChatEnabled(),
                room.getWatchingGame() == null ? null : GameResponse.from(room.getWatchingGame()),
                room.getCreatedAt()
        );
    }
}
