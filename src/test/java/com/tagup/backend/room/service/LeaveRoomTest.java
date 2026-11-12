package com.tagup.backend.room.service;

import com.tagup.backend.bet.entity.BetStatus;
import com.tagup.backend.bet.repository.BetRepository;
import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.game.repository.GameRepository;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.entity.RoomMember;
import com.tagup.backend.room.repository.RoomMemberRepository;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 더그아웃 나가기.
 *
 * <p>2026-10-16까지 <b>나가는 방법 자체가 없었다.</b> 한 번 들어간 방은 목록에 영구히 남았다.
 */
class LeaveRoomTest {

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final RoomMemberRepository roomMemberRepository = mock(RoomMemberRepository.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final BetRepository betRepository = mock(BetRepository.class);

    private final RoomService service = new RoomService(
            roomRepository, roomMemberRepository, gameRepository, betRepository);

    private final User user = user(1L, "나");
    private final Room room = room();

    @Test
    @DisplayName("멤버십이 사라진다")
    void leaves() {
        RoomMember member = member();
        when(roomMemberRepository.findByRoomAndUser(room, user)).thenReturn(Optional.of(member));
        when(roomMemberRepository.countByRoom(room)).thenReturn(1L);

        service.leaveRoom(10L, user);

        verify(roomMemberRepository).delete(member);
    }

    @Test
    @DisplayName("정산이 남은 내기가 있으면 나갈 수 없다 — 상대방이 영문 모르고 기다리게 된다")
    void blockedByUnsettledBet() {
        when(roomMemberRepository.findByRoomAndUser(room, user)).thenReturn(Optional.of(member()));
        when(betRepository.existsUnsettledBetOf(eq(room), eq(user), anyList())).thenReturn(true);

        assertThatThrownBy(() -> service.leaveRoom(10L, user))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BET_IN_PROGRESS);

        verify(roomMemberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("끝난 내기만 있으면 나갈 수 있다")
    void settledBetsDoNotBlock() {
        when(roomMemberRepository.findByRoomAndUser(room, user)).thenReturn(Optional.of(member()));
        when(betRepository.existsUnsettledBetOf(room, user,
                List.of(BetStatus.PENDING, BetStatus.ACCEPTED))).thenReturn(false);
        when(roomMemberRepository.countByRoom(room)).thenReturn(1L);

        service.leaveRoom(10L, user);

        verify(roomMemberRepository).delete(any());
    }

    @Test
    @DisplayName("마지막 한 명이 나가면 빈 더그아웃을 정리한다")
    void deletesEmptyRoom() {
        when(roomMemberRepository.findByRoomAndUser(room, user)).thenReturn(Optional.of(member()));
        when(roomMemberRepository.countByRoom(room)).thenReturn(0L);
        when(betRepository.existsByRoom(room)).thenReturn(false);

        service.leaveRoom(10L, user);

        verify(roomRepository).delete(room);
    }

    @Test
    @DisplayName("내기 기록이 남은 더그아웃은 비어도 지우지 않는다")
    void keepsRoomWithBetHistory() {
        when(roomMemberRepository.findByRoomAndUser(room, user)).thenReturn(Optional.of(member()));
        when(roomMemberRepository.countByRoom(room)).thenReturn(0L);
        when(betRepository.existsByRoom(room)).thenReturn(true);

        service.leaveRoom(10L, user);

        verify(roomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("멤버가 아니면 거절한다")
    void notAMember() {
        when(roomMemberRepository.findByRoomAndUser(room, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leaveRoom(10L, user))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ROOM_MEMBER);
    }

    private RoomMember member() {
        return RoomMember.builder().room(room).user(user).build();
    }

    private Room room() {
        Room room = Room.builder().tagCode("ABC123").name("테스트").createdBy(user).build();
        ReflectionTestUtils.setField(room, "id", 10L);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        return room;
    }

    private User user(Long id, String nickname) {
        User user = User.builder().firebaseUid("uid-" + id).email(id + "@t.app").nickname(nickname).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
