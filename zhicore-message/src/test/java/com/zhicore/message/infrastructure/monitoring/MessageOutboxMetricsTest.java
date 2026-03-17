package com.zhicore.message.infrastructure.monitoring;

import com.zhicore.message.infrastructure.config.MessageOutboxProperties;
import com.zhicore.message.infrastructure.mq.MessageOutboxTaskStatus;
import com.zhicore.message.infrastructure.repository.mapper.MessageOutboxTaskMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MessageOutboxMetrics 测试")
class MessageOutboxMetricsTest {

    @Test
    @DisplayName("应该上报消息 outbox backlog 与失败指标")
    void shouldCollectMessageOutboxMetrics() {
        MessageOutboxTaskMapper messageOutboxTaskMapper = mock(MessageOutboxTaskMapper.class);
        MessageOutboxProperties properties = new MessageOutboxProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageOutboxMetrics metrics = new MessageOutboxMetrics(messageOutboxTaskMapper, properties, meterRegistry);

        when(messageOutboxTaskMapper.countByStatus(MessageOutboxTaskStatus.PENDING.name())).thenReturn(7L);
        when(messageOutboxTaskMapper.findOldestPendingCreatedAt()).thenReturn(OffsetDateTime.now().minusSeconds(95));
        when(messageOutboxTaskMapper.countSucceededSince(org.mockito.ArgumentMatchers.any())).thenReturn(11L);
        when(messageOutboxTaskMapper.countFailedSince(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(properties.getMaxRetry()))).thenReturn(2L);
        when(messageOutboxTaskMapper.countDeadSince(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(properties.getMaxRetry()))).thenReturn(1L);

        metrics.collectMetrics();

        assertEquals(7.0, meterRegistry.get("domain.event.queue.pending.count").tag("queue", "message-outbox").gauge().value());
        double oldestAge = meterRegistry.get("domain.event.queue.pending.oldest.age.seconds").tag("queue", "message-outbox").gauge().value();
        assertTrue(oldestAge >= 94 && oldestAge <= 96);
        assertEquals(11.0, meterRegistry.get("domain.event.queue.dispatch.rate.per.minute").tag("queue", "message-outbox").gauge().value());
        assertEquals(2.0, meterRegistry.get("domain.event.queue.failure.rate.per.minute").tag("queue", "message-outbox").gauge().value());
        assertEquals(1.0, meterRegistry.get("domain.event.queue.dead.rate.per.minute").tag("queue", "message-outbox").gauge().value());
    }
}
