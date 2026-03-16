package com.zhicore.comment.application.service.command;

import com.zhicore.comment.application.dto.CommentOutboxRetryResponseDTO;
import com.zhicore.comment.application.dto.CommentOutboxSummaryDTO;
import com.zhicore.comment.domain.model.OutboxEventStatus;
import com.zhicore.comment.domain.repository.OutboxEventRepository;
import com.zhicore.comment.infrastructure.mq.CommentOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 评论 outbox 管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentOutboxAdminService {

    private final OutboxEventRepository outboxEventRepository;
    private final CommentOutboxDispatcher commentOutboxDispatcher;

    /**
     * 查询 outbox 状态摘要，便于判断是否存在积压或死信。
     */
    public CommentOutboxSummaryDTO getSummary() {
        return CommentOutboxSummaryDTO.builder()
                .pendingCount(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING))
                .failedCount(outboxEventRepository.countByStatus(OutboxEventStatus.FAILED))
                .deadCount(outboxEventRepository.countByStatus(OutboxEventStatus.DEAD))
                .succeededCount(outboxEventRepository.countByStatus(OutboxEventStatus.SUCCEEDED))
                .oldestPendingCreatedAt(outboxEventRepository.findOldestPendingCreatedAt())
                .build();
    }

    /**
     * 批量重试 DEAD 事件，主要用于基础设施故障恢复后的联动回放。
     */
    public CommentOutboxRetryResponseDTO retryDeadEvents(Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("operatorId 不能为空");
        }

        int retriedCount = commentOutboxDispatcher.retryDeadEvents();
        long pendingCount = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
        long deadCount = outboxEventRepository.countByStatus(OutboxEventStatus.DEAD);

        log.info("Comment outbox dead retry accepted: operatorId={}, retriedCount={}, pendingCount={}, deadCount={}",
                operatorId, retriedCount, pendingCount, deadCount);

        return CommentOutboxRetryResponseDTO.builder()
                .retriedCount(retriedCount)
                .pendingCount(pendingCount)
                .deadCount(deadCount)
                .build();
    }
}
