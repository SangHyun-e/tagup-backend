-- ============================================================
-- V2: 푸시 알림 기기 등록 테이블
-- 생성일: 2026-08-26 · Sprint 3.5 FCM 푸시
--
-- push_token = Expo push token (ExponentPushToken[...])
--   한 유저가 여러 기기를 가질 수 있어 별도 테이블로 분리.
--   토큰은 기기 고유값이므로 unique — 재로그인/기기 공유 시 소유자만 갱신된다.
-- ============================================================

CREATE TABLE user_devices (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    push_token VARCHAR(255) NOT NULL,
    platform   VARCHAR(20),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_user_devices_push_token UNIQUE (push_token),
    CONSTRAINT fk_user_devices_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_user_devices_user_id ON user_devices (user_id);
