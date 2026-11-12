package com.tagup.backend.safety.entity;

/** 신고 사유. 앱에서 고르게 하고, 직접 설명은 선택이다 */
public enum ReportReason {
    /** 욕설·비방 */
    ABUSE,
    /** 음란·선정성 */
    SEXUAL,
    /** 광고·스팸 */
    SPAM,
    /** 금전 내기 유도 — 이 앱에서는 금지다 */
    MONEY_BETTING,
    ETC
}
