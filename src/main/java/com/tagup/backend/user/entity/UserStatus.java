package com.tagup.backend.user.entity;

/**
 * 유저 상태. <b>자체 블랙리스트</b>다.
 *
 * <p>Firebase에서 계정을 비활성화해도 이미 발급된 ID 토큰은 최대 1시간 더 유효하다
 * ({@code verifyIdToken}은 서명과 만료만 본다). 검증 시 취소 여부까지 확인하는 옵션은
 * 요청마다 Firebase 조회가 붙는다. 그래서 차단·탈퇴 판단은 우리 DB에서 한다.
 */
public enum UserStatus {
    /** 정상 */
    ACTIVE,
    /** 운영자가 차단 — 모든 요청 거부 */
    BLOCKED,
    /** 본인이 탈퇴 — 모든 요청 거부 */
    WITHDRAWN;

    public boolean canUseService() {
        return this == ACTIVE;
    }
}
