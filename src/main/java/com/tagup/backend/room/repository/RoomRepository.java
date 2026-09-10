package com.tagup.backend.room.repository;

import com.tagup.backend.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByTagCode(String tagCode);
    boolean existsByTagCode(String tagCode);

    // 특정 팀을 응원하는 멤버가 있는 더그아웃 조회
    @Query("SELECT DISTINCT rm.room FROM RoomMember rm WHERE rm.user.favoriteTeam.id IN :teamIds")
    List<Room> findRoomsHavingMembersWithFavoriteTeamIn(@Param("teamIds") Set<Long> teamIds);

    // 채팅이 활성화된 더그아웃 전체 조회
    List<Room> findByChatEnabled(boolean chatEnabled);
}
