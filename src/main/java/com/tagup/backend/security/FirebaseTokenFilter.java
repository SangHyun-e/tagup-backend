package com.tagup.backend.security;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class FirebaseTokenFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            User user = resolveUser(token);
            if (user != null) {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    private User resolveUser(String token) {
        // Firebase 미초기화 시 (로컬 개발 모드)
        if (FirebaseApp.getApps().isEmpty()) {
            // 실제 Firebase JWT(3파트 base64)는 검증 불가 — 401 처리
            if (isJwt(token)) {
                log.debug("[Firebase] 미초기화 상태에서 Firebase JWT 수신 — 인증 불가 (FIREBASE_PROJECT_ID 설정 필요)");
                return null;
            }
            // 개발 모드: 짧은 uid 문자열로 직접 조회
            return userRepository.findByFirebaseUid(token).orElse(null);
        }

        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
            String uid = decoded.getUid();
            String email = decoded.getEmail();

            return userRepository.findByFirebaseUid(uid)
                    .or(() -> {
                        // 이미 이메일로 가입된 유저가 있으면 Firebase UID 연결
                        return userRepository.findByEmail(email).map(u -> {
                            u.linkFirebaseUid(uid);
                            return userRepository.save(u);
                        });
                    })
                    .orElse(null); // 미가입 유저 — sync 엔드포인트에서 생성

        } catch (Exception e) {
            log.debug("[Firebase] 토큰 검증 실패: {}", e.getMessage());
            return null;
        }
    }

    private boolean isJwt(String token) {
        return token.length() > 100 && token.chars().filter(c -> c == '.').count() == 2;
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
