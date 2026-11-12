package com.tagup.backend.bet.entity;

/** 배팅 종류. 정산 경로가 완전히 다르다. */
public enum BetType {
    /** 승패 배팅 — 경기가 끝나면 최종 스코어로 정산한다 */
    WIN_LOSE,
    /** 타석 배팅 — 그 타석이 끝나는 순간 아웃/세이프로 정산한다 */
    AT_BAT
}
