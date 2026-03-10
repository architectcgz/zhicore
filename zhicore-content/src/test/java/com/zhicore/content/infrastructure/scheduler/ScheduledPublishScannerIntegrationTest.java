package com.zhicore.content.infrastructure.scheduler;

import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.model.ScheduledPublishEventRecord;
import com.zhicore.content.application.model.ScheduledPublishEventRecord.ScheduledPublishStatus;
import com.zhicore.content.application.port.store.ScheduledPublishEventStore;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
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
    private ScheduledPublishEventStore scheduledPublishEventStore;

    @Autowired
    private PostRepository postRepository;

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
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();

        // 准备一个到点事件（status=SCHEDULED_PENDING）
        ScheduledPublishEventRecord e = ScheduledPublishEventRecord.builder()
                .eventId("init")
                .postId(1001L)
                .scheduledAt(dbNow.minusMinutes(1))
                .status(ScheduledPublishStatus.SCHEDULED_PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .createdAt(dbNow)
                .updatedAt(dbNow)
                .build();
        scheduledPublishEventStore.save(e);

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
        ScheduledPublishEventRecord stored = scheduledPublishEventStore.findActiveByPostId(1001L)
                .orElseThrow();
        assertNotNull(stored.getLastEnqueueAt());
        assertNotNull(stored.getEventId());
        assertNotEquals("init", stored.getEventId());
    }

    @Test
    @DisplayName("resetStaleGate：超过 10 分钟的闸门可被重置")
    void resetStaleGateShouldClearLastEnqueueAt() {
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();

        ScheduledPublishEventRecord e = ScheduledPublishEventRecord.builder()
                .eventId("stale-1")
                .postId(2001L)
                .scheduledAt(dbNow.minusMinutes(1))
                .status(ScheduledPublishStatus.SCHEDULED_PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .lastEnqueueAt(dbNow.minusMinutes(20))
                .createdAt(dbNow.minusMinutes(30))
                .updatedAt(dbNow.minusMinutes(20))
                .build();
        scheduledPublishEventStore.save(e);

        scanner.resetStaleGate();

        ScheduledPublishEventRecord stored = scheduledPublishEventStore.findActiveByPostId(2001L)
                .orElseThrow();
        assertNull(stored.getLastEnqueueAt());
        assertNotNull(stored.getLastError());
        assertTrue(stored.getLastError().contains("补扫重置"));
    }

    // ==================== 1.1 未到发布时间的处理 ====================

    @Test
    @DisplayName("scanAndEnqueue：未到点事件不应被扫描入队")
    void scanShouldSkipNotYetDueEvents() {
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();

        // 准备一个未到点事件（scheduledAt 在未来）
        ScheduledPublishEventRecord e = ScheduledPublishEventRecord.builder()
                .eventId("future-1")
                .postId(3001L)
                .scheduledAt(dbNow.plusMinutes(30))
                .status(ScheduledPublishStatus.SCHEDULED_PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .createdAt(dbNow)
                .updatedAt(dbNow)
                .build();
        scheduledPublishEventStore.save(e);

        scanner.scanAndEnqueue();

        // 未到点事件不应触发发布
        verify(integrationEventPublisher, never()).publish(any());

        // 事件状态保持 SCHEDULED_PENDING
        ScheduledPublishEventRecord stored = scheduledPublishEventStore.findActiveByPostId(3001L)
                .orElseThrow();
        assertEquals(ScheduledPublishStatus.SCHEDULED_PENDING, stored.getStatus());
        assertNull(stored.getLastEnqueueAt());
    }

    // ==================== 1.2 到点幂等发布 ====================

    @Test
    @DisplayName("publishScheduledIfNeeded：已发布文章应幂等返回（affected_rows==0）")
    void publishScheduledIfNeededShouldBeIdempotentForPublishedPost() {
        long postIdValue = System.currentTimeMillis();
        PostId postId = PostId.of(postIdValue);

        // 创建一篇已发布的文章
        Post post = Post.createDraft(postId, UserId.of(1000L), "幂等发布测试");
        postRepository.save(post);

        // 直接用 SQL 将状态改为 PUBLISHED（模拟已发布）
        jdbcTemplate.update(
                "UPDATE posts SET status = 1, published_at = CURRENT_TIMESTAMP WHERE id = ?",
                postIdValue
        );

        // 再次调用 publishScheduledIfNeeded，应返回 0（幂等 no-op）
        var result = postRepository.publishScheduledIfNeeded(postIdValue, LocalDateTime.now());
        // affected_rows == 0 时返回 empty
        assertThat(result).isEmpty();
    }

    // ==================== 1.4 扫描入队闸门并发测试 ====================

    @Test
    @DisplayName("CAS 闸门：并发扫描时只有一个实例成功入队")
    void casGateShouldAllowOnlyOneEnqueueConcurrently() throws InterruptedException {
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();

        ScheduledPublishEventRecord e = ScheduledPublishEventRecord.builder()
                .eventId("cas-concurrent")
                .postId(4001L)
                .scheduledAt(dbNow.minusMinutes(1))
                .status(ScheduledPublishStatus.SCHEDULED_PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .createdAt(dbNow)
                .updatedAt(dbNow)
                .build();
        scheduledPublishEventStore.save(e);

        // 重新读取以获取 ID
        ScheduledPublishEventRecord saved = scheduledPublishEventStore.findActiveByPostId(4001L)
                .orElseThrow();

        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String newEventId = "cas-" + i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    int affected = scheduledPublishEventStore.casUpdateLastEnqueueAt(saved, dbNow, newEventId);
                    if (affected == 1) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // 只有一个线程成功
        assertEquals(1, successCount.get(), "CAS 闸门应保证只有一个线程成功入队");
    }

    // ==================== 1.5 补扫重置：未到点事件不被误重置 ====================

    @Test
    @DisplayName("resetStaleGate：未到点事件不应被补扫重置")
    void resetStaleGateShouldNotResetFutureEvents() {
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();

        // 准备一个未到点但 lastEnqueueAt 超时的事件
        ScheduledPublishEventRecord e = ScheduledPublishEventRecord.builder()
                .eventId("future-stale")
                .postId(5001L)
                .scheduledAt(dbNow.plusMinutes(30))
                .status(ScheduledPublishStatus.SCHEDULED_PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .lastEnqueueAt(dbNow.minusMinutes(20))
                .createdAt(dbNow.minusMinutes(30))
                .updatedAt(dbNow.minusMinutes(20))
                .build();
        scheduledPublishEventStore.save(e);

        scanner.resetStaleGate();

        // 未到点事件不应被重置（findStaleScheduledPending 限定 scheduledAt <= dbNow）
        ScheduledPublishEventRecord stored = scheduledPublishEventStore.findActiveByPostId(5001L)
                .orElseThrow();
        assertNotNull(stored.getLastEnqueueAt(), "未到点事件的 lastEnqueueAt 不应被重置");
    }

    // ==================== 1.7 时间基准测试 ====================

    @Test
    @DisplayName("dbNow：数据库时间与应用时间差异在合理范围内")
    void dbNowShouldBeCloseToAppNow() {
        LocalDateTime appNow = LocalDateTime.now();
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();

        // 数据库时间与应用时间差异不应超过 5 秒（合理的网络/处理延迟）
        long diffSeconds = Math.abs(
                java.time.Duration.between(appNow, dbNow).getSeconds()
        );
        assertThat(diffSeconds).isLessThan(5L);
    }
}
