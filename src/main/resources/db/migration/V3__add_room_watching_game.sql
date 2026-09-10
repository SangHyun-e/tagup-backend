-- ============================================================
-- V3: 더그아웃의 "오늘 같이 보는 경기"
-- 생성일: 2026-09-09 · Sprint 4 타석 배팅
--
-- 더그아웃은 영구 그룹이고 경기는 매일 바뀐다. 방에 팀을 고정하면 매일 방을 새로
-- 만들어야 하므로, 방은 그대로 두고 이 컬럼만 날마다 갈아끼운다.
-- 스코어보드·라이브 피드·타석 배팅이 이 경기를 기준으로 붙고,
-- 배팅 생성 시 gameId를 생략하면 이 값이 쓰인다.
--
-- NULL 허용 — 멤버들의 응원팀이 오늘 경기에 없으면 비워두고 직접 고르게 한다.
-- ON DELETE SET NULL: 경기 행이 정리돼도 더그아웃은 살아 있어야 한다.
-- ============================================================

ALTER TABLE rooms
    ADD COLUMN watching_game_id BIGINT NULL;

ALTER TABLE rooms
    ADD CONSTRAINT fk_rooms_watching_game
        FOREIGN KEY (watching_game_id) REFERENCES games (id)
        ON DELETE SET NULL;

CREATE INDEX idx_rooms_watching_game_id ON rooms (watching_game_id);
