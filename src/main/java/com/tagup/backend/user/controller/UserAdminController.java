package com.tagup.backend.user.controller;

import com.tagup.backend.common.response.ApiResponse;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 운영자용 유저 관리. {@code /api/v1/admin/**} 은 {@code SecurityConfig} 에서 ADMIN 권한을 요구한다.
 */
@Tag(name = "UserAdmin", description = "유저 관리 (관리자 전용)")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "계정 차단 — 다음 요청부터 모든 API가 거부된다")
    @PostMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> block(
            @PathVariable Long userId,
            @AuthenticationPrincipal User admin) {
        userAdminService.block(userId, admin);
        return ResponseEntity.ok(ApiResponse.ok("차단했습니다."));
    }

    @Operation(summary = "계정 차단 해제")
    @PostMapping("/{userId}/unblock")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @PathVariable Long userId,
            @AuthenticationPrincipal User admin) {
        userAdminService.unblock(userId, admin);
        return ResponseEntity.ok(ApiResponse.ok("차단을 해제했습니다."));
    }
}
