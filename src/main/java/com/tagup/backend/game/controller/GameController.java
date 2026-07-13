package com.tagup.backend.game.controller;

import com.tagup.backend.common.response.ApiResponse;
import com.tagup.backend.game.dto.GameResponse;
import com.tagup.backend.game.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Game", description = "KBO 경기 일정 및 결과")
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @Operation(summary = "경기 목록 조회", description = "날짜별 경기 조회 (date 미입력 시 오늘)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GameResponse>>> getGames(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(gameService.getGamesByDate(targetDate)));
    }

    @Operation(summary = "오늘 경기 조회")
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getTodayGames() {
        return ResponseEntity.ok(ApiResponse.ok(gameService.getTodayGames()));
    }

    @Operation(summary = "다가오는 예정 경기 조회", description = "오늘 이후 가장 가까운 예정 경기일의 경기 목록 (최대 3주 탐색, 휴식기 대응)")
    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<GameResponse>>> getUpcomingGames() {
        return ResponseEntity.ok(ApiResponse.ok(gameService.getUpcomingGames()));
    }

    @Operation(summary = "경기 상세 조회")
    @GetMapping("/{gameId}")
    public ResponseEntity<ApiResponse<GameResponse>> getGame(@PathVariable Long gameId) {
        return ResponseEntity.ok(ApiResponse.ok(gameService.getGame(gameId)));
    }
}
