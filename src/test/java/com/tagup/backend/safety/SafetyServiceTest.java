package com.tagup.backend.safety;

import com.tagup.backend.common.exception.CustomException;
import com.tagup.backend.common.exception.ErrorCode;
import com.tagup.backend.safety.dto.CreateReportRequest;
import com.tagup.backend.safety.entity.Report;
import com.tagup.backend.safety.entity.ReportReason;
import com.tagup.backend.safety.entity.ReportStatus;
import com.tagup.backend.safety.entity.UserBlock;
import com.tagup.backend.safety.repository.ReportRepository;
import com.tagup.backend.safety.repository.UserBlockRepository;
import com.tagup.backend.safety.service.BlockService;
import com.tagup.backend.safety.service.ReportService;
import com.tagup.backend.user.entity.User;
import com.tagup.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 신고·차단.
 *
 * <p>애플 심사 지침 1.2는 사용자가 글을 쓰는 앱에 <b>신고 수단과 차단 수단</b>을 요구한다.
 * 2026-10-16까지 둘 다 없었다.
 */
class SafetyServiceTest {

    private final UserBlockRepository blockRepository = mock(UserBlockRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final BlockService blockService = new BlockService(blockRepository, userRepository);
    private final ReportService reportService = new ReportService(reportRepository, userRepository);

    private final User me = user(1L, "나");
    private final User other = user(2L, "상대");

    @Nested
    @DisplayName("차단")
    class Block {

        @Test
        void 차단하면_기록이_남는다() {
            blockService.block(me, 2L);

            ArgumentCaptor<UserBlock> captor = ArgumentCaptor.forClass(UserBlock.class);
            verify(blockRepository).save(captor.capture());
            assertThat(captor.getValue().getBlocked()).isEqualTo(other);
        }

        @Test
        @DisplayName("이미 차단한 사람을 또 차단해도 오류를 내지 않는다 — 두 번 누르는 건 흔한 일이다")
        void 중복_차단은_조용히_넘어간다() {
            when(blockRepository.existsByBlockerAndBlocked(me, other)).thenReturn(true);

            blockService.block(me, 2L);

            verify(blockRepository, never()).save(any());
        }

        @Test
        void 자기_자신은_차단할_수_없다() {
            assertThatThrownBy(() -> blockService.block(me, 1L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_BLOCK_SELF);
        }

        @Test
        void 해제하면_기록이_사라진다() {
            UserBlock block = UserBlock.builder().blocker(me).blocked(other).build();
            when(blockRepository.findByBlockerAndBlocked(me, other)).thenReturn(Optional.of(block));

            blockService.unblock(me, 2L);

            verify(blockRepository).delete(block);
        }

        @Test
        void 차단하지_않은_사람을_해제해도_조용하다() {
            when(blockRepository.findByBlockerAndBlocked(me, other)).thenReturn(Optional.empty());

            blockService.unblock(me, 2L);

            verify(blockRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("신고")
    class Reporting {

        @Test
        @DisplayName("메시지 내용을 사본으로 남긴다 — 상대가 지워도 운영자가 볼 근거가 있어야 한다")
        void 메시지_사본을_보관한다() {
            reportService.report(me, request("msg-1", "욕설이 담긴 메시지"));

            ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
            verify(reportRepository).save(captor.capture());
            Report saved = captor.getValue();
            assertThat(saved.getMessageContent()).isEqualTo("욕설이 담긴 메시지");
            assertThat(saved.getReason()).isEqualTo(ReportReason.ABUSE);
            assertThat(saved.getStatus()).isEqualTo(ReportStatus.PENDING);
        }

        @Test
        void 같은_메시지를_두_번_신고하면_한_번만_접수된다() {
            when(reportRepository.existsByReporterAndMessageId(me, "msg-1")).thenReturn(true);

            reportService.report(me, request("msg-1", "내용"));

            verify(reportRepository, never()).save(any());
        }

        @Test
        void 자기_자신은_신고할_수_없다() {
            CreateReportRequest selfReport = new CreateReportRequest(
                    1L, ReportReason.ABUSE, null, null, null, null);

            assertThatThrownBy(() -> reportService.report(me, selfReport))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_REPORT_SELF);
        }

        @Test
        @DisplayName("사람만 신고하는 경우 메시지 없이도 접수된다")
        void 메시지_없이도_신고할_수_있다() {
            reportService.report(me, new CreateReportRequest(
                    2L, ReportReason.SPAM, 10L, null, null, "계속 광고를 보냅니다"));

            verify(reportRepository).save(any());
        }

        @Test
        void 처리하면_상태가_바뀐다() {
            Report report = Report.builder()
                    .reporter(me).reported(other).reason(ReportReason.ABUSE).build();
            when(reportRepository.findById(5L)).thenReturn(Optional.of(report));

            reportService.resolve(5L, "경고 조치", true);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        }

        @Test
        @DisplayName("조치하지 않고 종결할 수도 있다")
        void 조치_없이_종결() {
            Report report = Report.builder()
                    .reporter(me).reported(other).reason(ReportReason.ETC).build();
            when(reportRepository.findById(5L)).thenReturn(Optional.of(report));

            reportService.resolve(5L, "문제 없음", false);

            assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
        }

        private CreateReportRequest request(String messageId, String content) {
            return new CreateReportRequest(2L, ReportReason.ABUSE, 10L, messageId, content, null);
        }
    }

    private User user(Long id, String nickname) {
        User user = User.builder()
                .firebaseUid("uid-" + id).email(id + "@tagup.app").nickname(nickname).build();
        ReflectionTestUtils.setField(user, "id", id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }
}
