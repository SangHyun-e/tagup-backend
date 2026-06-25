package com.tagup.backend.room.dto;

import com.tagup.backend.room.entity.Room;

import java.time.LocalDateTime;

public record RoomResponse(
        Long id,
        String tagCode,
        String name,
        String createdByNickname,
        LocalDateTime createdAt
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getTagCode(),
                room.getName(),
                room.getCreatedBy().getNickname(),
                room.getCreatedAt()
        );
    }
}
