package com.tagup.backend.room.service;

import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.room.dto.CreateRoomRequest;
import com.tagup.backend.room.dto.JoinRoomRequest;
import com.tagup.backend.room.dto.RoomMemberResponse;
import com.tagup.backend.room.dto.RoomResponse;
import com.tagup.backend.room.entity.Room;
import com.tagup.backend.room.entity.RoomMember;
import com.tagup.backend.room.repository.RoomMemberRepository;
import com.tagup.backend.room.repository.RoomRepository;
import com.tagup.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final String TAG_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TAG_CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request, User user) {
        String tagCode = generateUniqueTagCode();

        Room room = Room.builder()
                .tagCode(tagCode)
                .name(request.name())
                .createdBy(user)
                .build();

        roomRepository.save(room);

        RoomMember member = RoomMember.builder()
                .room(room)
                .user(user)
                .build();

        roomMemberRepository.save(member);

        return RoomResponse.from(room);
    }

    @Transactional
    public RoomResponse joinRoom(JoinRoomRequest request, User user) {
        Room room = roomRepository.findByTagCode(request.tagCode().toUpperCase())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TAG_CODE));

        if (roomMemberRepository.existsByRoomAndUser(room, user)) {
            throw new CustomException(ErrorCode.ALREADY_ROOM_MEMBER);
        }

        RoomMember member = RoomMember.builder()
                .room(room)
                .user(user)
                .build();

        roomMemberRepository.save(member);

        return RoomResponse.from(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getMyRooms(User user) {
        return roomMemberRepository.findAllByUserWithRoom(user).stream()
                .map(rm -> RoomResponse.from(rm.getRoom()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoom(Long roomId, User user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        if (!roomMemberRepository.existsByRoomAndUser(room, user)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return RoomResponse.from(room);
    }

    @Transactional(readOnly = true)
    public List<RoomMemberResponse> getRoomMembers(Long roomId, User user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        if (!roomMemberRepository.existsByRoomAndUser(room, user)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return roomMemberRepository.findAllByRoomWithUser(room).stream()
                .map(RoomMemberResponse::from)
                .toList();
    }

    private String generateUniqueTagCode() {
        String code;
        do {
            code = generateTagCode();
        } while (roomRepository.existsByTagCode(code));
        return code;
    }

    private String generateTagCode() {
        StringBuilder sb = new StringBuilder(TAG_CODE_LENGTH);
        for (int i = 0; i < TAG_CODE_LENGTH; i++) {
            sb.append(TAG_CODE_CHARS.charAt(RANDOM.nextInt(TAG_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
