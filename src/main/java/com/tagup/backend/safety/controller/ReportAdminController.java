package com.tagup.backend.safety.controller;

import com.tagup.backend.common.response.ApiResponse;
import com.tagup.backend.safety.dto.ReportResponse;
import com.tagup.backend.safety.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 신고 처리. {@code /api/v1/admin/**} 은 SecurityConfig 에서 ADMIN 권한을 요구한다 */
@Tag(name = "ReportAdmin", description = "신고 처리 (관리자 전용)")
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ReportAdminController {

    private final ReportService reportService;

    @Operation(summary = "접수된 신고 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReportResponse>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.pendingReports()));
    }

    @Operation(summary = "신고 처리", description = "actionTaken=false 면 조치 없이 종결한다")
    @PostMapping("/{reportId}/resolve")
    public ResponseEntity<ApiResponse<Void>> resolve(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "true") boolean actionTaken,
            @RequestParam(required = false) String note) {
        reportService.resolve(reportId, note, actionTaken);
        return ResponseEntity.ok(ApiResponse.ok("처리했습니다."));
    }
}
