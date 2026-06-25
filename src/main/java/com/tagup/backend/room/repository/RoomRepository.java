package com.tagup.backend.room.repository;

import com.tagup.backend.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByTagCode(String tagCode);
    boolean existsByTagCode(String tagCode);
}
