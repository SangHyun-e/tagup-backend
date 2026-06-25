package com.tagup.backend.room.repository;

import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.entity.RoomMember;
import com.tagup.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    boolean existsByRoomAndUser(Room room, User user);

    @Query("SELECT rm FROM RoomMember rm JOIN FETCH rm.room WHERE rm.user = :user")
    List<RoomMember> findAllByUserWithRoom(@Param("user") User user);
}
