-- 유저 권한·상태 추가 (2026-10-01)
--
-- role   : USER / ADMIN — /api/v1/admin/** 접근 권한. Firebase 커스텀 클레임이 아니라 DB를 기준으로 삼는다
-- status : ACTIVE / BLOCKED / WITHDRAWN — 자체 블랙리스트.
--          Firebase에서 계정을 비활성화해도 이미 발급된 ID 토큰은 최대 1시간 통과하므로
--          실제 차단은 매 요청 조회하는 이 컬럼으로 한다.
--
-- 기존 행이 있어도 안전하도록 DEFAULT 를 준다.

ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_users_status ON users (status);
