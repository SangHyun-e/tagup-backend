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

    @Operation(summary = "KBO 크롤러 수동 실행")
    @PostMapping("/crawl")
    public ResponseEntity<ApiResponse<Void>> crawl(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        gameCrawlService.crawlAndSave(target);
        return ResponseEntity.ok(ApiResponse.ok("크롤링 완료: " + target));
    }
}
