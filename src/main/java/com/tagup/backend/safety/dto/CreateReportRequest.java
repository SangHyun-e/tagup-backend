package com.tagup.backend.safety.dto;

import com.tagup.backend.safety.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 요청.
 *
 * <p>{@code messageContent} 를 앱이 함께 보내는 이유는 메시지가 Firestore에 있어 서버가 읽을 수
 * 없기 때문이다. 신고 뒤 상대가 지우면 운영자가 볼 근거가 사라진다.
 */
public record CreateReportRequest(
        @NotNull(message = "신고 대상을 선택해주세요.")
        Long reportedUserId,

        @NotNull(message = "신고 사유를 선택해주세요.")
        ReportReason reason,

        Long roomId,

        @Size(max = 64)
        String messageId,

        @Size(max = 1000)
        String messageContent,

        @Size(max = 500, message = "설명은 500자까지 쓸 수 있습니다.")
        String detail
) {}
