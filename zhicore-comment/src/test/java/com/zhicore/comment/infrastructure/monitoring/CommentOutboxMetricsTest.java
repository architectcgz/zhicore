package com.zhicore.comment.infrastructure.monitoring;

import com.zhicore.comment.domain.model.OutboxEventStatus;
import com.zhicore.comment.domain.repository.OutboxEventRepository;
import com.zhicore.comment.infrastructure.config.CommentOutboxProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CommentOutboxMetrics 测试")
class CommentOutboxMetricsTest {

    @Test
    @DisplayName("应该上报评论 outbox backlog 与失败指标")
    void shouldCollectCommentOutboxMetrics() {
        OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        CommentOutboxProperties properties = new CommentOutboxProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CommentOutboxMetrics metrics = new CommentOutboxMetrics(outboxEventRepository, properties, meterRegistry);

        when(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(3L);
        when(outboxEventRepository.findOldestPendingCreatedAt()).thenReturn(LocalDateTime.now().minusSeconds(210));
        when(outboxEventRepository.countSucceededSince(org.mockito.ArgumentMatchers.any())).thenReturn(5L);
        when(outboxEventRepository.countFailedSince(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(properties.getMaxRetry()))).thenReturn(1L);
        when(outboxEventRepository.countDeadSince(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(properties.getMaxRetry()))).thenReturn(2L);

        metrics.collectMetrics();

        assertEquals(3.0, meterRegistry.get("domain.event.queue.pending.count").tag("queue", "comment-outbox").gauge().value());
        double oldestAge = meterRegistry.get("domain.event.queue.pending.oldest.age.seconds").tag("queue", "comment-outbox").gauge().value();
        assertTrue(oldestAge >= 209 && oldestAge <= 211);
        assertEquals(5.0, meterRegistry.get("domain.event.queue.dispatch.rate.per.minute").tag("queue", "comment-outbox").gauge().value());
        assertEquals(1.0, meterRegistry.get("domain.event.queue.failure.rate.per.minute").tag("queue", "comment-outbox").gauge().value());
        assertEquals(2.0, meterRegistry.get("domain.event.queue.dead.rate.per.minute").tag("queue", "comment-outbox").gauge().value());
    }
}
