-- 신고·차단 (2026-10-16)
--
-- 애플 심사 지침 1.2는 사용자가 글을 쓰는 앱에 신고·차단 수단을 요구한다. 없으면 반려된다.
--
-- user_blocks : 사용자끼리의 차단. 운영자가 거는 계정 정지(users.status)와 다르다.
--               차단당한 사람은 서비스를 계속 쓰고, 차단한 사람 눈에만 안 보인다.
-- reports     : 신고. 메시지는 Firestore에 있어 서버가 읽을 수 없으므로
--               신고 시점의 내용을 message_content 에 복사해 둔다 (상대가 지워도 남게).

CREATE TABLE user_blocks (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    blocker_id BIGINT NOT NULL,
    blocked_id BIGINT NOT NULL,
    created_at DATETIME(6),
    CONSTRAINT uk_user_blocks UNIQUE (blocker_id, blocked_id),
    CONSTRAINT fk_user_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users (id),
    CONSTRAINT fk_user_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE reports (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id     BIGINT       NOT NULL,
    reported_id     BIGINT       NOT NULL,
    room_id         BIGINT,
    message_id      VARCHAR(64),
    message_content VARCHAR(1000),
    reason          VARCHAR(20)  NOT NULL,
    detail          VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    admin_note      VARCHAR(500),
    created_at      DATETIME(6),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_reports_reported FOREIGN KEY (reported_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_reports_status ON reports (status);
