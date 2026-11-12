package com.tagup.backend.notification.service;

import com.tagup.backend.notification.entity.UserDevice;
import com.tagup.backend.notification.repository.UserDeviceRepository;
import com.tagup.backend.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 푸시 알림 발송.
 *
 * <p>Expo 앱이므로 Expo Push Service를 경유한다 (내부적으로 FCM/APNs로 전달).
 * Firebase Admin SDK 직접 발송은 네이티브 FCM 토큰이 필요해 Expo Go에서 테스트가 불가능하다.
 *
 * <p><b>발송 실패는 절대 비즈니스 로직을 깨뜨리지 않는다.</b> 모든 예외를 삼키고 로그만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushSender {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private static final int BATCH_SIZE = 100; // Expo 권장 상한

    private final UserDeviceRepository userDeviceRepository;
    private final RestTemplate restTemplate;

    /** 지정 유저들에게 발송. 대상이 없으면 조용히 종료 */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void sendToUsers(Collection<User> users, String title, String body,
                            Map<String, String> data) {
        try {
            List<User> targets = users.stream().filter(Objects::nonNull).distinct().toList();
            if (targets.isEmpty()) return;

            List<String> tokens = userDeviceRepository.findAllByUserIn(targets).stream()
                    .map(UserDevice::getPushToken)
                    .distinct()
                    .toList();
            if (tokens.isEmpty()) {
                log.debug("[푸시] 등록된 기기 없음 — 발송 생략 (title={})", title);
                return;
            }

            for (int i = 0; i < tokens.size(); i += BATCH_SIZE) {
                send(tokens.subList(i, Math.min(i + BATCH_SIZE, tokens.size())), title, body, data);
            }
            log.info("[푸시] {}건 발송 — {}", tokens.size(), title);
        } catch (Exception e) {
            log.warn("[푸시] 발송 실패 (무시하고 계속): {}", e.getMessage());
        }
    }

    private void send(List<String> tokens, String title, String body, Map<String, String> data) {
        List<Map<String, Object>> messages = tokens.stream()
                .map(token -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("to", token);
                    m.put("title", title);
                    m.put("body", body);
                    m.put("sound", "default");
                    if (data != null && !data.isEmpty()) m.put("data", data);
                    return m;
                })
                .toList();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");

        restTemplate.postForEntity(EXPO_PUSH_URL,
                new HttpEntity<>(messages, headers), String.class);
    }
}
