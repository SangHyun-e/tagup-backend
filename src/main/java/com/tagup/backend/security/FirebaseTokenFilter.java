package com.tagup.backend.security;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.entity.UserStatus;
import com.tagup.backend.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
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
                // 차단·탈퇴 계정은 여기서 끊는다. Firebase에서 계정을 비활성화해도 이미 발급된
                // ID 토큰은 최대 1시간 더 통과하므로, 우리 DB 상태가 실질적인 차단 수단이다.
                if (!user.canUseService()) {
                    reject(response, user.getStatus());
                    return;
                }
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 차단·탈퇴 사유를 그대로 알려준다 — 앱이 "왜 안 되는지"를 보여줄 수 있어야 한다 */
    private void reject(HttpServletResponse response, UserStatus status) throws IOException {
        String message = status == UserStatus.WITHDRAWN
                ? "탈퇴한 계정입니다."
                : "이용이 제한된 계정입니다.";
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }

    private User resolveUser(String token) {
        if (FirebaseApp.getApps().isEmpty()) {
            // 로컬 개발 모드: JWT면 payload 디코딩으로 uid 추출, 아니면 token을 uid로 직접 사용
            String uid = isJwt(token) ? extractUidFromJwt(token) : token;
            if (uid == null) return null;
            return userRepository.findByFirebaseUid(uid).orElse(null);
        }

        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
            String uid = decoded.getUid();
            String email = decoded.getEmail();

            return userRepository.findByFirebaseUid(uid)
                    .or(() -> userRepository.findByEmail(email).map(u -> {
                        u.linkFirebaseUid(uid);
                        return userRepository.save(u);
                    }))
                    .orElse(null);

        } catch (Exception e) {
            log.debug("[Firebase] 토큰 검증 실패: {}", e.getMessage());
            return null;
        }
    }

    // 로컬 개발 모드 전용: 서명 미검증, uid만 추출
    private String extractUidFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonNode payload = JsonMapper.builder().build().readTree(payloadBytes);
            return payload.path("user_id").asString(payload.path("sub").asString(null));
        } catch (Exception e) {
            log.debug("[Firebase] JWT payload 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private boolean isJwt(String token) {
        return token.length() > 100 && token.chars().filter(c -> c == '.').count() == 2;
    }

    private String padBase64(String s) {
        return s + "=".repeat((4 - s.length() % 4) % 4);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
