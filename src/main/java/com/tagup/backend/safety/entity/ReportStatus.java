package com.tagup.backend.safety.entity;

/** 신고 처리 상태 */
public enum ReportStatus {
    /** 접수됨 */
    PENDING,
    /** 운영자가 확인하고 조치함 */
    RESOLVED,
    /** 확인했으나 조치하지 않음 */
    DISMISSED
}
