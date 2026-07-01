package com.tagup.backend.user.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.team.entity.Team;
import com.tagup.backend.team.repository.TeamRepository;
import com.tagup.backend.user.dto.SyncUserRequest;
import com.tagup.backend.user.dto.UserProfileResponse;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    /**
     * Firebase ID Token으로 유저를 조회 또는 생성하고, 닉네임을 저장합니다.
     * - 신규 유저: Firebase UID + email + nickname으로 생성
     * - 기존 유저: 닉네임 업데이트
     */
    @Transactional
    public UserProfileResponse sync(String firebaseToken, SyncUserRequest request) {
        FirebaseUserInfo firebaseInfo = verifyToken(firebaseToken);

        User user = userRepository.findByFirebaseUid(firebaseInfo.uid())
                .or(() -> userRepository.findByEmail(firebaseInfo.email()).map(u -> {
                    u.linkFirebaseUid(firebaseInfo.uid());
                    return u;
                }))
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .firebaseUid(firebaseInfo.uid())
                                .email(firebaseInfo.email())
                                .nickname(request.nickname())
                                .build()
                ));

        user.updateNickname(request.nickname());

        if (request.teamId() != null) {
            Team team = teamRepository.findById(request.teamId())
                    .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
            user.updateFavoriteTeam(team);
        }

        return UserProfileResponse.from(user);
    }

    private FirebaseUserInfo verifyToken(String token) {
        if (token == null || token.isBlank()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }

        // Firebase 미초기화 (로컬 개발 모드): 토큰을 uid로 직접 사용
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("[Firebase] 미초기화 상태 — 토큰을 uid로 직접 사용 (개발 모드)");
            String email = token.contains("@") ? token : token + "@dev.local";
            return new FirebaseUserInfo(token, email);
        }

        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
            return new FirebaseUserInfo(decoded.getUid(), decoded.getEmail());
        } catch (Exception e) {
            log.warn("[Firebase] 토큰 검증 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_FIREBASE_TOKEN);
        }
    }

    private record FirebaseUserInfo(String uid, String email) {}
}
