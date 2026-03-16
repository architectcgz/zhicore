package com.zhicore.comment.application.service;

import com.zhicore.comment.application.dto.CommentOutboxRetryResponseDTO;
import com.zhicore.comment.application.dto.CommentOutboxSummaryDTO;
import com.zhicore.comment.application.service.command.CommentOutboxAdminService;
import com.zhicore.comment.domain.model.OutboxEventStatus;
import com.zhicore.comment.domain.repository.OutboxEventRepository;
import com.zhicore.comment.infrastructure.mq.CommentOutboxDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Comment outbox 管理服务测试")
class CommentOutboxAdminServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private CommentOutboxDispatcher commentOutboxDispatcher;

    @Test
    @DisplayName("应返回 outbox 摘要统计")
    void shouldReturnSummary() {
        LocalDateTime oldestPending = LocalDateTime.of(2026, 3, 16, 10, 0);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(3L);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.FAILED)).thenReturn(2L);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.DEAD)).thenReturn(1L);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.SUCCEEDED)).thenReturn(20L);
        when(outboxEventRepository.findOldestPendingCreatedAt()).thenReturn(oldestPending);

        CommentOutboxAdminService service = new CommentOutboxAdminService(outboxEventRepository, commentOutboxDispatcher);
        CommentOutboxSummaryDTO summary = service.getSummary();

        assertEquals(3L, summary.getPendingCount());
        assertEquals(2L, summary.getFailedCount());
        assertEquals(1L, summary.getDeadCount());
        assertEquals(20L, summary.getSucceededCount());
        assertEquals(oldestPending, summary.getOldestPendingCreatedAt());
    }

    @Test
    @DisplayName("批量重试 dead 时应返回最新统计")
    void shouldRetryDeadEvents() {
        when(commentOutboxDispatcher.retryDeadEvents()).thenReturn(52);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(52L);
        when(outboxEventRepository.countByStatus(OutboxEventStatus.DEAD)).thenReturn(0L);

        CommentOutboxAdminService service = new CommentOutboxAdminService(outboxEventRepository, commentOutboxDispatcher);
        CommentOutboxRetryResponseDTO response = service.retryDeadEvents(1001L);

        assertEquals(52, response.getRetriedCount());
        assertEquals(52L, response.getPendingCount());
        assertEquals(0L, response.getDeadCount());
    }

    @Test
    @DisplayName("操作人为空时应拒绝重试")
    void shouldRejectRetryWhenOperatorIdMissing() {
        CommentOutboxAdminService service = new CommentOutboxAdminService(outboxEventRepository, commentOutboxDispatcher);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.retryDeadEvents(null));

        assertEquals("operatorId 不能为空", exception.getMessage());
    }
}
