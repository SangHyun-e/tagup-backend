package com.tagup.backend.bet.controller;

import com.tagup.backend.bet.dto.BetResponse;
import com.tagup.backend.bet.dto.CreateAtBatBetRequest;
import com.tagup.backend.bet.dto.CreateBetRequest;
import com.tagup.backend.bet.service.BetService;
import com.tagup.backend.common.response.ApiResponse;
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

@Tag(name = "Bet", description = "내기 시스템")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequiredArgsConstructor
public class BetController {

    private final BetService betService;

    @Operation(summary = "내기 제안")
    @PostMapping("/api/v1/rooms/{roomId}/bets")
    public ResponseEntity<ApiResponse<BetResponse>> createBet(
            @PathVariable Long roomId,
            @Valid @RequestBody CreateBetRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("내기를 제안했습니다.", betService.createBet(roomId, request, user)));
    }

    @Operation(summary = "타석 배팅 (지금 진행 중인 타석의 아웃/세이프)",
            description = "더그아웃이 보고 있는 경기의 '지금 타석'에 겁니다. 어느 타석인지는 서버가 정합니다. "
                    + "타석 감지 후 30초 안에만 받습니다 — 결과를 본 뒤 거는 것을 막기 위해서입니다.")
    @PostMapping("/api/v1/rooms/{roomId}/bets/at-bat")
    public ResponseEntity<ApiResponse<BetResponse>> createAtBatBet(
            @PathVariable Long roomId,
            @Valid @RequestBody CreateAtBatBetRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(ApiResponse.ok("타석 배팅을 걸었습니다.",
                betService.createAtBatBet(roomId, request, user)));
    }

    @Operation(summary = "더그아웃 내기 목록 조회")
    @GetMapping("/api/v1/rooms/{roomId}/bets")
    public ResponseEntity<ApiResponse<List<BetResponse>>> getRoomBets(
            @PathVariable Long roomId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(betService.getRoomBets(roomId, user)));
    }

    @Operation(summary = "콜! (내기 수락)")
    @PutMapping("/api/v1/bets/{betId}/accept")
    public ResponseEntity<ApiResponse<BetResponse>> acceptBet(
            @PathVariable Long betId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("내기를 수락했습니다.", betService.acceptBet(betId, user)));
    }

    @Operation(summary = "내기 취소")
    @PutMapping("/api/v1/bets/{betId}/cancel")
    public ResponseEntity<ApiResponse<BetResponse>> cancelBet(
            @PathVariable Long betId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok("내기를 취소했습니다.", betService.cancelBet(betId, user)));
    }
}
