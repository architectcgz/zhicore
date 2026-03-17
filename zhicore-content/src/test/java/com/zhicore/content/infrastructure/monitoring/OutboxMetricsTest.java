package com.zhicore.content.infrastructure.monitoring;

import com.zhicore.content.infrastructure.config.OutboxProperties;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxEventMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OutboxMetrics 测试")
class OutboxMetricsTest {

    @Test
    @DisplayName("应该上报统一 durable queue 指标")
    void shouldCollectDurableQueueMetrics() {
        OutboxEventMapper outboxEventMapper = mock(OutboxEventMapper.class);
        OutboxProperties properties = new OutboxProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OutboxMetrics metrics = new OutboxMetrics(outboxEventMapper, properties, meterRegistry);

        when(outboxEventMapper.countByStatus(OutboxEventEntity.OutboxStatus.PENDING.name())).thenReturn(5L);
        when(outboxEventMapper.findOldestPendingCreatedAt()).thenReturn(Instant.now().minusSeconds(120));
        when(outboxEventMapper.countSucceededSince(org.mockito.ArgumentMatchers.any())).thenReturn(8L);
        when(outboxEventMapper.countFailedSince(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(properties.getMaxRetry()))).thenReturn(2L);
        when(outboxEventMapper.countDeadSince(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(properties.getMaxRetry()))).thenReturn(1L);

        metrics.collectMetrics();

        assertEquals(5.0, meterRegistry.get("domain.event.queue.pending.count").tag("queue", "content-outbox").gauge().value());
        double oldestAge = meterRegistry.get("domain.event.queue.pending.oldest.age.seconds").tag("queue", "content-outbox").gauge().value();
        assertTrue(oldestAge >= 119 && oldestAge <= 121);
        assertEquals(8.0, meterRegistry.get("domain.event.queue.dispatch.rate.per.minute").tag("queue", "content-outbox").gauge().value());
        assertEquals(2.0, meterRegistry.get("domain.event.queue.failure.rate.per.minute").tag("queue", "content-outbox").gauge().value());
        assertEquals(1.0, meterRegistry.get("domain.event.queue.dead.rate.per.minute").tag("queue", "content-outbox").gauge().value());
    }
}
