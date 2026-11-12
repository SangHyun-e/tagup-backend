package com.tagup.backend.game.controller;

import com.tagup.backend.common.response.ApiResponse;
import com.tagup.backend.game.service.GameCrawlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "GameAdmin", description = "경기 관리 (local 전용)")
@RestController
@RequestMapping("/api/v1/admin/games")
@RequiredArgsConstructor
@Profile("local")
public class GameAdminController {

    private final GameCrawlService gameCrawlService;

    @Operation(summary = "특정 날짜 크롤 (실시간 결과 업데이트용)")
    @PostMapping("/crawl")
    public ResponseEntity<ApiResponse<Void>> crawlByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        gameCrawlService.crawlAndSave(target);
        return ResponseEntity.ok(ApiResponse.ok("크롤링 완료: " + target));
    }

    @Operation(summary = "월간 크롤 (해당 월 전체 경기 일정 수집)")
    @PostMapping("/crawl/month")
    public ResponseEntity<ApiResponse<Void>> crawlByMonth(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate today = LocalDate.now();
        int y = year != null ? year : today.getYear();
        int m = month != null ? month : today.getMonthValue();
        gameCrawlService.crawlAndSaveMonth(y, m);
        return ResponseEntity.ok(ApiResponse.ok(String.format("월간 크롤링 완료: %d-%02d", y, m)));
    }
}
