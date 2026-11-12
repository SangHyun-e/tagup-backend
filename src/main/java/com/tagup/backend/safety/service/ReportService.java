package com.tagup.backend.safety.service;

import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.safety.dto.CreateReportRequest;
import com.tagup.backend.safety.dto.ReportResponse;
import com.tagup.backend.safety.entity.Report;
import com.tagup.backend.safety.entity.ReportStatus;
import com.tagup.backend.safety.repository.ReportRepository;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 신고 접수와 처리.
 *
 * <p>신고된 메시지는 Firestore에 있어 서버가 읽을 수 없다. 그래서 앱이 <b>메시지 내용을 함께
 * 보내고</b> 우리는 그 사본을 보관한다. 신고 뒤 상대가 메시지를 지워도 운영자가 볼 근거가 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @Transactional
    public void report(User reporter, CreateReportRequest request) {
        if (reporter.getId().equals(request.reportedUserId())) {
            throw new CustomException(ErrorCode.CANNOT_REPORT_SELF);
        }

        User reported = userRepository.findById(request.reportedUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 같은 메시지를 두 번 신고해도 접수만 늘 뿐이다. 조용히 넘긴다
        if (request.messageId() != null
                && reportRepository.existsByReporterAndMessageId(reporter, request.messageId())) {
            return;
        }

        reportRepository.save(Report.builder()
                .reporter(reporter)
                .reported(reported)
                .roomId(request.roomId())
                .messageId(request.messageId())
                .messageContent(request.messageContent())
                .reason(request.reason())
                .detail(request.detail())
                .build());

        log.warn("[신고] {} → {} 사유 {}", reporter.getId(), reported.getId(), request.reason());
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> pendingReports() {
        return reportRepository.findByStatusWithUsers(ReportStatus.PENDING).stream()
                .map(ReportResponse::from)
                .toList();
    }

    @Transactional
    public void resolve(Long reportId, String adminNote, boolean actionTaken) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));

        if (actionTaken) report.resolve(adminNote);
        else report.dismiss(adminNote);
    }
}
