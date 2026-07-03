package com.tagup.backend.bet.entity;

public enum BetStatus {
    PENDING,    // 제안됨 (수락 대기)
    ACCEPTED,   // 수락됨 (경기 진행 중)
    FINISHED,   // 정산 완료
    CANCELLED   // 취소됨
}
