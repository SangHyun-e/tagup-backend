-- ============================================================
-- V4: 타석 배팅
-- 생성일: 2026-09-14 · Sprint 4
--
-- 승패 배팅과 정산 경로가 완전히 다르다.
--   WIN_LOSE : 경기 종료 후 최종 스코어로 정산 (BetSettlementScheduler)
--   AT_BAT   : 그 타석이 끝나는 순간 아웃/세이프로 정산 (AtBatBetSettler)
--
-- KBO가 타석 ID를 주지 않으므로 이닝 + 초/말 + 타자 이름으로 타석을 식별한다.
-- bet_on_team_id 는 승패 배팅에만 쓰이므로 NULL 을 허용하도록 바꾼다.
-- ============================================================

ALTER TABLE bets
    ADD COLUMN type                 VARCHAR(20) NOT NULL DEFAULT 'WIN_LOSE',
    ADD COLUMN bet_on_at_bat_result VARCHAR(10) NULL,
    ADD COLUMN at_bat_inning        INT         NULL,
    ADD COLUMN at_bat_half          VARCHAR(10) NULL,
    ADD COLUMN at_bat_batter        VARCHAR(30) NULL;

-- 기존 행은 전부 승패 배팅이다 (DEFAULT 로 이미 채워짐)
ALTER TABLE bets
    MODIFY COLUMN bet_on_team_id BIGINT NULL;

-- 타석 종료 때마다 "이 경기의 미정산 타석 배팅"을 찾는다
CREATE INDEX idx_bets_game_type_status ON bets (game_id, type, status);
