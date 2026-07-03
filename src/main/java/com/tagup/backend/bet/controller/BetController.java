package com.tagup.backend.bet.controller;

import com.tagup.backend.bet.dto.BetResponse;
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
