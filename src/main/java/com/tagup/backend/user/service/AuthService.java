package com.tagup.backend.user.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
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

import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

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

        // Firebase 미초기화 (로컬 개발 모드)
        if (FirebaseApp.getApps().isEmpty()) {
            if (isJwt(token)) {
                // 서명 검증 없이 payload만 디코딩 (개발 모드 전용)
                return decodeJwtPayload(token);
            }
            // 짧은 uid 문자열 직접 사용
            log.warn("[Firebase] 미초기화 — uid 직접 사용 (개발 모드): {}", token);
            return new FirebaseUserInfo(token, token + "@dev.local");
        }

        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
            return new FirebaseUserInfo(decoded.getUid(), decoded.getEmail());
        } catch (Exception e) {
            log.warn("[Firebase] 토큰 검증 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_FIREBASE_TOKEN);
        }
    }

    // 로컬 개발 모드 전용: 서명 미검증, uid/email만 추출
    private FirebaseUserInfo decodeJwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonNode payload = JsonMapper.builder().build().readTree(payloadBytes);

            String uid = payload.path("user_id").asString(payload.path("sub").asString());
            String email = payload.path("email").asString(uid + "@firebase.local");

            log.warn("[Firebase] 미초기화 — JWT 서명 미검증, uid={} email={} (개발 모드)", uid, email);
            return new FirebaseUserInfo(uid, email);
        } catch (Exception e) {
            log.warn("[Firebase] JWT payload 파싱 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_FIREBASE_TOKEN);
        }
    }

    private boolean isJwt(String token) {
        return token.length() > 100 && token.chars().filter(c -> c == '.').count() == 2;
    }

    private String padBase64(String s) {
        return s + "=".repeat((4 - s.length() % 4) % 4);
    }

    private record FirebaseUserInfo(String uid, String email) {}
}
