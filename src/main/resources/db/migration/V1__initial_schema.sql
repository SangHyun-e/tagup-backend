-- ============================================================
-- TagUp 초기 스키마 (MySQL 8 / AWS RDS)
-- 생성일: 2026-08-26 · 대상: Sprint 3.5 최초 배포
--
-- ⚠ prod는 `ddl-auto: validate` 이므로 Hibernate가 테이블을 만들지 않는다.
--    이 스크립트를 먼저 적용하지 않으면 서버가 기동조차 하지 않는다.
--
-- 적용:
--   mysql -h <RDS_ENDPOINT> -u <USER> -p tagupdb < V1__initial_schema.sql
--
-- 참고: KBO 10개 구단 데이터는 DataInitializer가 첫 기동 시 자동 삽입하므로
--       별도 시드 SQL이 필요 없다 (teams 테이블이 비어 있을 때만 동작).
-- ============================================================

-- 데이터베이스가 없다면 아래를 먼저 실행 (한글 저장을 위해 utf8mb4 필수)
-- CREATE DATABASE tagupdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- teams : KBO 10개 구단
-- ------------------------------------------------------------
CREATE TABLE teams (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    short_name  VARCHAR(10)  NOT NULL,
    logo_url    VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- users : Firebase 인증 유저 (password는 레거시, 현재 미사용)
-- ------------------------------------------------------------
CREATE TABLE users (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    email        VARCHAR(255) NOT NULL,
    password     VARCHAR(255),
    nickname     VARCHAR(20)  NOT NULL,
    firebase_uid VARCHAR(255),
    team_id      BIGINT,
    created_at   DATETIME(6),
    updated_at   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email        UNIQUE (email),
    CONSTRAINT uk_users_firebase_uid UNIQUE (firebase_uid),
    CONSTRAINT fk_users_team FOREIGN KEY (team_id) REFERENCES teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- rooms : 더그아웃
--   chat_key = Firestore 채팅 경로 키(UUID). 숫자 id는 재사용될 수 있어
--   채팅 경로에 쓰지 않는다 (BE #11)
-- ------------------------------------------------------------
CREATE TABLE rooms (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    tag_code     VARCHAR(6)  NOT NULL,
    chat_key     VARCHAR(36) NOT NULL,
    name         VARCHAR(50) NOT NULL,
    created_by   BIGINT      NOT NULL,
    chat_enabled BIT(1)      NOT NULL,
    created_at   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_rooms_tag_code UNIQUE (tag_code),
    CONSTRAINT uk_rooms_chat_key UNIQUE (chat_key),
    CONSTRAINT fk_rooms_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- room_members : 더그아웃 참여자 (한 번 입장하면 영구 멤버)
-- ------------------------------------------------------------
CREATE TABLE room_members (
    id        BIGINT NOT NULL AUTO_INCREMENT,
    room_id   BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,
    joined_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_room_members_room_user UNIQUE (room_id, user_id),
    CONSTRAINT fk_room_members_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_room_members_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- games : KBO 경기 (크롤러가 kbo_game_id 기준 upsert)
--   inning = 진행 중일 때만 값 존재 (BE #17)
-- ------------------------------------------------------------
CREATE TABLE games (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    kbo_game_id  VARCHAR(255) NOT NULL,
    game_date    DATE         NOT NULL,
    game_time    TIME(6),
    home_team_id BIGINT       NOT NULL,
    away_team_id BIGINT       NOT NULL,
    status       ENUM('CANCELLED','FINISHED','IN_PROGRESS','SCHEDULED') NOT NULL,
    home_score   INTEGER,
    away_score   INTEGER,
    inning       INTEGER,
    stadium      VARCHAR(50),
    updated_at   DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT idx_games_kbo_game_id UNIQUE (kbo_game_id),
    CONSTRAINT fk_games_home_team FOREIGN KEY (home_team_id) REFERENCES teams (id),
    CONSTRAINT fk_games_away_team FOREIGN KEY (away_team_id) REFERENCES teams (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_games_game_date ON games (game_date);

-- ------------------------------------------------------------
-- bets : 오픈 배팅
--   receiver_id는 NULL 허용 — 콜하기 전까지 상대가 없다 (BE #14)
--   bet_on_team_id는 의도적으로 FK가 아님 (엔티티가 연관관계로 두지 않음)
-- ------------------------------------------------------------
CREATE TABLE bets (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    proposer_id     BIGINT       NOT NULL,
    receiver_id     BIGINT,
    room_id         BIGINT       NOT NULL,
    game_id         BIGINT       NOT NULL,
    content         VARCHAR(100) NOT NULL,
    bet_on_team_id  BIGINT       NOT NULL,
    status          ENUM('ACCEPTED','CANCELLED','FINISHED','PENDING') NOT NULL,
    proposer_result ENUM('DRAW','LOSE','WIN'),
    created_at      DATETIME(6),
    updated_at      DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_bets_proposer FOREIGN KEY (proposer_id) REFERENCES users (id),
    CONSTRAINT fk_bets_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
    CONSTRAINT fk_bets_room     FOREIGN KEY (room_id)     REFERENCES rooms (id),
    CONSTRAINT fk_bets_game     FOREIGN KEY (game_id)     REFERENCES games (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_bets_room_id ON bets (room_id);
CREATE INDEX idx_bets_game_id ON bets (game_id);
