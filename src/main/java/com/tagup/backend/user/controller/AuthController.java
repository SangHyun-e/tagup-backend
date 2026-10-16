package com.tagup.backend.user.controller;

import com.tagup.backend.common.response.ApiResponse;
import com.tagup.backend.user.dto.SyncUserRequest;
import com.tagup.backend.user.dto.UserProfileResponse;
import com.tagup.backend.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Firebase 인증 연동")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Firebase 유저 동기화 (최초 가입 / 프로필 초기화)",
            description = "Firebase 로그인 후 호출. Bearer <Firebase ID Token> 필수. " +
                    "닉네임을 DB에 저장하고 유저 프로필을 반환합니다. " +
                    "이미 가입된 유저면 닉네임을 업데이트합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<UserProfileResponse>> sync(
            HttpServletRequest httpRequest,
            @Valid @RequestBody SyncUserRequest request) {
        String firebaseToken = extractToken(httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("동기화 완료", authService.sync(firebaseToken, request)));
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
