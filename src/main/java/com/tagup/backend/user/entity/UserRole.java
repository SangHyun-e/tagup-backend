package com.tagup.backend.user.entity;

/**
 * 유저 권한.
 *
 * <p>Firebase 커스텀 클레임이 아니라 <b>우리 DB를 권한의 기준</b>으로 삼는다. 클레임은 토큰이
 * 갱신될 때까지(최대 1시간) 반영되지 않는 반면, 우리는 매 요청 uid로 유저를 조회하므로
 * 추가 비용 없이 즉시 반영된다.
 */
public enum UserRole {
    USER,
    ADMIN
}
