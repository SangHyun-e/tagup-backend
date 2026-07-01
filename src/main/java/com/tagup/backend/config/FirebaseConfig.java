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

    @PostConstruct
    public void init() {
        if (!FirebaseApp.getApps().isEmpty()) return;

        if (projectId.isBlank()) {
            log.warn("[Firebase] firebase.project-id 미설정 — Firebase 인증 비활성화됨 (로컬 개발 모드)");
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
            log.warn("[Firebase] 초기화 실패: {} — Firebase 인증 비활성화됨", e.getMessage());
        }
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
