package com.zhicore.content.infrastructure.scheduler;

import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.ScheduledPublishEventRepository;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.content.infrastructure.persistence.pg.entity.ScheduledPublishEventEntity;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 定时发布扫描任务集成测试（R1）
 *
 * 重点验证：
 * - 扫描入队 CAS 闸门（affected_rows==1 才会发布）
 * - 冷却期内重复扫描不重复入队
 * - 补扫可重置超时闸门
 */
@DisplayName("ScheduledPublishScanner 集成测试")
class ScheduledPublishScannerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ScheduledPublishScanner scanner;

    @Autowired
    private ScheduledPublishEventRepository scheduledPublishEventRepository;

    @MockBean
    private IntegrationEventPublisher integrationEventPublisher;

    @BeforeEach
    void setUp() {
        cleanupPostgres();
        cleanupMongoDB();
        cleanupRedis();
    }

    @Test
    @DisplayName("scanAndEnqueue：到点事件仅入队一次（冷却期内不重复发布）")
    void scanAndEnqueueShouldPublishOnce() {
        LocalDateTime dbNow = scheduledPublishEventRepository.dbNow();

        // 准备一个到点事件（status=SCHEDULED_PENDING）
        ScheduledPublishEventEntity e = new ScheduledPublishEventEntity();
        e.setEventId("init");
        e.setPostId(1001L);
        e.setScheduledAt(dbNow.minusMinutes(1));
        e.setStatus(ScheduledPublishEventEntity.ScheduledPublishStatus.SCHEDULED_PENDING);
        e.setRescheduleRetryCount(0);
        e.setPublishRetryCount(0);
        e.setCreatedAt(dbNow);
        e.setUpdatedAt(dbNow);
        scheduledPublishEventRepository.save(e);

        scanner.scanAndEnqueue();
        scanner.scanAndEnqueue();

        // 断言：只发布一次
        ArgumentCaptor<PostScheduleExecuteIntegrationEvent> captor =
                ArgumentCaptor.forClass(PostScheduleExecuteIntegrationEvent.class);
        verify(integrationEventPublisher, times(1)).publish(captor.capture());

        PostScheduleExecuteIntegrationEvent published = captor.getValue();
        assertNotNull(published.getEventId());
        assertEquals(1001L, published.getPostId());

        // 断言：eventId 已被 CAS 写入表中（last_enqueue_at 也应被更新）
        ScheduledPublishEventEntity stored = scheduledPublishEventRepository.findActiveByPostId(1001L)
                .orElseThrow();
        assertNotNull(stored.getLastEnqueueAt());
        assertNotNull(stored.getEventId());
        assertNotEquals("init", stored.getEventId());
    }

    @Test
    @DisplayName("resetStaleGate：超过 10 分钟的闸门可被重置")
    void resetStaleGateShouldClearLastEnqueueAt() {
        LocalDateTime dbNow = scheduledPublishEventRepository.dbNow();

        ScheduledPublishEventEntity e = new ScheduledPublishEventEntity();
        e.setEventId("stale-1");
        e.setPostId(2001L);
        e.setScheduledAt(dbNow.minusMinutes(1));
        e.setStatus(ScheduledPublishEventEntity.ScheduledPublishStatus.SCHEDULED_PENDING);
        e.setRescheduleRetryCount(0);
        e.setPublishRetryCount(0);
        e.setLastEnqueueAt(dbNow.minusMinutes(20));
        e.setCreatedAt(dbNow.minusMinutes(30));
        e.setUpdatedAt(dbNow.minusMinutes(20));
        scheduledPublishEventRepository.save(e);

        scanner.resetStaleGate();

        ScheduledPublishEventEntity stored = scheduledPublishEventRepository.findActiveByPostId(2001L)
                .orElseThrow();
        assertNull(stored.getLastEnqueueAt());
        assertNotNull(stored.getLastError());
        assertTrue(stored.getLastError().contains("补扫重置"));
    }
}

