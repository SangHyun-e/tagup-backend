package com.tagup.backend.safety.entity;

import com.tagup.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 신고. 애플 심사 지침 1.2가 요구하는 "offensive content를 신고할 수단"이다.
 *
 * <p>신고된 메시지는 Firestore에 있고 우리 DB에는 없다. 그래서 <b>메시지 내용을 복사해 둔다</b> —
 * 신고 뒤 상대가 메시지를 지우면 운영자가 볼 근거가 사라지기 때문이다.
 */
@Entity
@Table(name = "reports", indexes = @Index(name = "idx_reports_status", columnList = "status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_id", nullable = false)
    private User reported;

    /** 어느 더그아웃에서 일어난 일인지 (없을 수도 있다) */
    @Column(name = "room_id")
    private Long roomId;

    /** Firestore 메시지 id. 메시지가 아니라 사람을 신고한 경우 비어 있다 */
    @Column(length = 64)
    private String messageId;

    /** 신고 당시의 메시지 내용 사본 — 상대가 지워도 남는다 */
    @Column(length = 1000)
    private String messageContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(length = 500)
    private String adminNote;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Report(User reporter, User reported, Long roomId, String messageId,
                  String messageContent, ReportReason reason, String detail) {
        this.reporter = reporter;
        this.reported = reported;
        this.roomId = roomId;
        this.messageId = messageId;
        this.messageContent = messageContent;
        this.reason = reason;
        this.detail = detail;
        this.status = ReportStatus.PENDING;
    }

    public void resolve(String adminNote) {
        this.status = ReportStatus.RESOLVED;
        this.adminNote = adminNote;
    }

    public void dismiss(String adminNote) {
        this.status = ReportStatus.DISMISSED;
        this.adminNote = adminNote;
    }
}
