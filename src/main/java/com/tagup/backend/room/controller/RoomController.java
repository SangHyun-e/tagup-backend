package com.tagup.backend.room.controller;

import com.tagup.backend.common.response.ApiResponse;
import com.tagup.backend.room.dto.CreateRoomRequest;
import com.tagup.backend.room.dto.JoinRoomRequest;
import com.tagup.backend.room.dto.RoomResponse;
import com.tagup.backend.room.service.RoomService;
import com.tagup.backend.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Room", description = "더그아웃 (채팅방) 관리")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "더그아웃 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok("더그아웃이 생성되었습니다.", roomService.createRoom(request, user)));
    }

    @Operation(summary = "태그코드로 더그아웃 입장")
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<RoomResponse>> joinRoom(
            @Valid @RequestBody JoinRoomRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok("더그아웃에 입장했습니다.", roomService.joinRoom(request, user)));
    }

    @Operation(summary = "내 더그아웃 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getMyRooms(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(roomService.getMyRooms(user)));
    }

    @Operation(summary = "더그아웃 상세 조회")
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomResponse>> getRoom(
            @PathVariable Long roomId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(roomService.getRoom(roomId, user)));
    }
}
