package com.tagup.backend.safety.dto;

import com.tagup.backend.safety.entity.Report;
import com.tagup.backend.safety.entity.ReportReason;
import com.tagup.backend.safety.entity.ReportStatus;

import java.time.LocalDateTime;

/** 운영자가 보는 신고 */
public record ReportResponse(
        Long id,
        String reporterNickname,
        Long reportedUserId,
        String reportedNickname,
        ReportReason reason,
        String detail,
        Long roomId,
        String messageContent,
        ReportStatus status,
        LocalDateTime createdAt
) {

    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getReporter().getNickname(),
                report.getReported().getId(),
                report.getReported().getNickname(),
                report.getReason(),
                report.getDetail(),
                report.getRoomId(),
                report.getMessageContent(),
                report.getStatus(),
                report.getCreatedAt());
    }
}
