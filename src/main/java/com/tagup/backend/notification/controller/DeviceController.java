package com.tagup.backend.notification.controller;

import com.tagup.backend.common.response.ApiResponse;
import com.tagup.backend.notification.dto.RegisterDeviceRequest;
import com.tagup.backend.notification.service.DeviceService;
import com.tagup.backend.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification", description = "푸시 알림 기기 등록")
@RestController
@RequestMapping("/api/v1/users/me/devices")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "푸시 기기 등록", description = "앱 시작/로그인 시 Expo push token 등록 (동일 토큰은 갱신)")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> register(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid RegisterDeviceRequest request) {
        deviceService.register(user, request);
        return ResponseEntity.ok(ApiResponse.ok("기기를 등록했습니다."));
    }

    @Operation(summary = "푸시 기기 해제", description = "로그아웃 시 호출")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unregister(
            @AuthenticationPrincipal User user,
            @RequestParam String pushToken) {
        deviceService.unregister(pushToken);
        return ResponseEntity.ok(ApiResponse.ok("기기를 해제했습니다."));
    }
}
