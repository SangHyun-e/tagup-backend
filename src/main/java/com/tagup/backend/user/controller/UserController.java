package com.tagup.backend.user.controller;

import com.tagup.backend.common.response.ApiResponse;
import com.tagup.backend.user.dto.UpdateFavoriteTeamRequest;
import com.tagup.backend.user.dto.UserProfileResponse;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.service.UserService;
import com.tagup.backend.user.service.WithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "사용자 프로필")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final WithdrawService withdrawService;

    @Operation(summary = "내 프로필 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(user)));
    }

    @Operation(summary = "응원 구단 설정")
    @PutMapping("/me/team")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateFavoriteTeam(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid UpdateFavoriteTeamRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateFavoriteTeam(user, request)));
    }

    @Operation(summary = "회원 탈퇴",
            description = "계정을 지운다. 내기 기록에는 '탈퇴한 사용자'로 남고 개인정보는 제거된다. 되돌릴 수 없다.")
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal User user) {
        withdrawService.withdraw(user);
        return ResponseEntity.ok(ApiResponse.ok("탈퇴가 완료됐습니다."));
    }
}
