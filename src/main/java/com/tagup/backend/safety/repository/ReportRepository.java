package com.tagup.backend.safety.repository;

import com.tagup.backend.safety.entity.Report;
import com.tagup.backend.safety.entity.ReportStatus;
import com.tagup.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 같은 메시지를 두 번 신고하는 것을 막는다 */
    boolean existsByReporterAndMessageId(User reporter, String messageId);

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter JOIN FETCH r.reported "
         + "WHERE r.status = :status ORDER BY r.createdAt ASC")
    List<Report> findByStatusWithUsers(@Param("status") ReportStatus status);
}
