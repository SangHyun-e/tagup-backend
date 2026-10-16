package com.tagup.backend.safety.controller;

import com.tagup.backend.common.response.ApiResponse;
import com.tagup.backend.safety.dto.BlockedUserResponse;
import com.tagup.backend.safety.dto.CreateReportRequest;
import com.tagup.backend.safety.service.BlockService;
import com.tagup.backend.safety.service.ReportService;
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

/** 신고·차단. 애플 심사 지침 1.2가 요구하는 커뮤니티 안전장치다 */
@Tag(name = "Safety", description = "신고·차단")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SafetyController {

    private final BlockService blockService;
    private final ReportService reportService;

    @Operation(summary = "사용자 차단", description = "차단한 사람의 메시지는 내 화면에서 가려진다. 여러 번 눌러도 같다.")
    @PostMapping("/users/me/blocks/{userId}")
    public ResponseEntity<ApiResponse<Void>> block(
            @PathVariable Long userId, @AuthenticationPrincipal User me) {
        blockService.block(me, userId);
        return ResponseEntity.ok(ApiResponse.ok("차단했습니다."));
    }

    @Operation(summary = "차단 해제")
    @DeleteMapping("/users/me/blocks/{userId}")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @PathVariable Long userId, @AuthenticationPrincipal User me) {
        blockService.unblock(me, userId);
        return ResponseEntity.ok(ApiResponse.ok("차단을 해제했습니다."));
    }

    @Operation(summary = "내가 차단한 사람 목록", description = "앱이 이 목록으로 메시지를 가린다")
    @GetMapping("/users/me/blocks")
    public ResponseEntity<ApiResponse<List<BlockedUserResponse>>> myBlocks(
            @AuthenticationPrincipal User me) {
        return ResponseEntity.ok(ApiResponse.ok(blockService.myBlocks(me)));
    }

    @Operation(summary = "신고하기",
            description = "메시지는 Firestore에 있어 서버가 읽을 수 없다. 신고 시점의 내용을 함께 보내면 "
                    + "상대가 지워도 운영자가 볼 근거가 남는다.")
    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<Void>> report(
            @Valid @RequestBody CreateReportRequest request,
            @AuthenticationPrincipal User me) {
        reportService.report(me, request);
        return ResponseEntity.ok(ApiResponse.ok("신고가 접수됐습니다."));
    }
}
