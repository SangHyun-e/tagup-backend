package com.tagup.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.project-id:}")
    private String projectId;

    // 로컬: 서비스 계정 JSON 파일 경로
    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    // 프로덕션: Base64 인코딩된 서비스 계정 JSON (환경변수 FIREBASE_CREDENTIALS)
    @Value("${firebase.credentials-base64:}")
    private String credentialsBase64;

    /**
     * true면 Firebase 초기화 실패 시 기동을 중단한다.
     *
     * <p>Firebase가 없으면 {@code FirebaseTokenFilter}가 로컬 개발 모드로 떨어져
     * <b>Authorization 헤더의 문자열을 그대로 uid로 신뢰</b>한다. 로컬에서는 편의지만
     * 서버가 외부에 공개된 상태(터널/프로덕션)에서는 uid만 알면 사칭이 되므로,
     * 조용히 인증 없이 뜨는 것보다 기동을 실패시키는 편이 안전하다.
     */
    @Value("${tagup.security.require-firebase:false}")
    private boolean requireFirebase;

    @PostConstruct
    public void init() {
        if (!FirebaseApp.getApps().isEmpty()) return;

        if (projectId.isBlank()) {
            failOrWarn("firebase.project-id 미설정", null);
            return;
        }

        try {
            GoogleCredentials credentials = resolveCredentials();
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("[Firebase] Firebase Admin SDK 초기화 완료 (project={})", projectId);
        } catch (IOException e) {
            failOrWarn("자격증명 로딩 실패: " + e.getMessage(), e);
        }
    }

    private void failOrWarn(String reason, Throwable cause) {
        if (requireFirebase) {
            throw new IllegalStateException(
                    "[Firebase] " + reason + " — 이 프로파일은 Firebase 인증이 필수입니다. "
                            + "FIREBASE_PROJECT_ID / FIREBASE_CREDENTIALS(또는 FIREBASE_CREDENTIALS_PATH)를 설정하세요.",
                    cause);
        }
        log.warn("[Firebase] {} — Firebase 인증 비활성화됨 (로컬 개발 모드)", reason);
    }

    private GoogleCredentials resolveCredentials() throws IOException {
        if (!credentialsBase64.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(credentialsBase64);
            return GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
        }
        if (!credentialsPath.isBlank()) {
            return GoogleCredentials.fromStream(new FileInputStream(credentialsPath));
        }
        // GOOGLE_APPLICATION_CREDENTIALS 환경변수 사용
        return GoogleCredentials.getApplicationDefault();
    }
}
