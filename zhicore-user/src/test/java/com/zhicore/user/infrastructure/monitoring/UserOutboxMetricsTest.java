package com.zhicore.user.infrastructure.monitoring;

import com.zhicore.user.domain.model.OutboxEventStatus;
import com.zhicore.user.domain.repository.OutboxEventRepository;
import com.zhicore.user.infrastructure.config.UserOutboxProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("UserOutboxMetrics 测试")
class UserOutboxMetricsTest {

    @Test
    @DisplayName("应该上报用户 outbox backlog 与失败指标")
    void shouldCollectUserOutboxMetrics() {
        OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        UserOutboxProperties properties = new UserOutboxProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        UserOutboxMetrics metrics = new UserOutboxMetrics(outboxEventRepository, properties, meterRegistry);

        when(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(4L);
        when(outboxEventRepository.findOldestPendingCreatedAt()).thenReturn(OffsetDateTime.now().minusSeconds(150));
        when(outboxEventRepository.countSucceededSince(org.mockito.ArgumentMatchers.any())).thenReturn(6L);
        when(outboxEventRepository.countFailedSince(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(properties.getMaxRetry()))).thenReturn(2L);
        when(outboxEventRepository.countDeadSince(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(properties.getMaxRetry()))).thenReturn(1L);

        metrics.collectMetrics();

        assertEquals(4.0, meterRegistry.get("domain.event.queue.pending.count").tag("queue", "user-outbox").gauge().value());
        double oldestAge = meterRegistry.get("domain.event.queue.pending.oldest.age.seconds").tag("queue", "user-outbox").gauge().value();
        assertTrue(oldestAge >= 149 && oldestAge <= 151);
        assertEquals(6.0, meterRegistry.get("domain.event.queue.dispatch.rate.per.minute").tag("queue", "user-outbox").gauge().value());
        assertEquals(2.0, meterRegistry.get("domain.event.queue.failure.rate.per.minute").tag("queue", "user-outbox").gauge().value());
        assertEquals(1.0, meterRegistry.get("domain.event.queue.dead.rate.per.minute").tag("queue", "user-outbox").gauge().value());
    }
}
