package com.zhicore.content.infrastructure.scheduler;

import com.zhicore.content.application.model.ScheduledPublishEventRecord;
import com.zhicore.content.application.model.ScheduledPublishEventRecord.ScheduledPublishStatus;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.ScheduledPublishEventStore;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.content.infrastructure.config.ScheduledPublishProperties;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("ScheduledPublishScanner 集成测试")
class ScheduledPublishScannerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ScheduledPublishScanner scanner;

    @Autowired
    private ScheduledPublishEventStore scheduledPublishEventStore;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ScheduledPublishProperties properties;

    @MockBean
    private IntegrationEventPublisher integrationEventPublisher;

    @BeforeEach
    void setUp() {
        cleanupPostgres();
        cleanupMongoDB();
        cleanupRedis();
    }

    @Test
    @DisplayName("scanAndEnqueue：claim 后补偿重发一次，并回到 PENDING")
    void scanAndEnqueueShouldClaimAndRepublishOnce() {
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();

        ScheduledPublishEventRecord record = ScheduledPublishEventRecord.builder()
                .eventId("task-1")
                .postId(1001L)
                .scheduledAt(dbNow.minusMinutes(1))
                .nextAttemptAt(dbNow.minusSeconds(1))
                .status(ScheduledPublishStatus.PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .createdAt(dbNow)
                .updatedAt(dbNow)
                .build();
        scheduledPublishEventStore.save(record);

        scanner.scanAndEnqueue();
        scanner.scanAndEnqueue();

        ArgumentCaptor<PostScheduleExecuteIntegrationEvent> captor =
                ArgumentCaptor.forClass(PostScheduleExecuteIntegrationEvent.class);
        verify(integrationEventPublisher, times(1)).publish(captor.capture());

        PostScheduleExecuteIntegrationEvent published = captor.getValue();
        assertEquals(1001L, published.getPostId());
        assertEquals("task-1", published.getScheduledPublishEventId());
        assertEquals(0, published.getDelayLevel());

        ScheduledPublishEventRecord stored = scheduledPublishEventStore.findByEventId("task-1").orElseThrow();
        assertEquals(ScheduledPublishStatus.PENDING, stored.getStatus());
        assertNotNull(stored.getTriggerEventId());
        assertNotNull(stored.getNextAttemptAt());
        assertNull(stored.getClaimedAt());
        assertNull(stored.getClaimedBy());
    }

    @Test
    @DisplayName("scanAndEnqueue：nextAttemptAt 未到的远期任务不应补偿重发")
    void scanShouldSkipFutureTasksBeforeNextAttemptAt() {
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();
        LocalDateTime scheduledAt = dbNow.plusMinutes(30);

        ScheduledPublishEventRecord record = ScheduledPublishEventRecord.builder()
                .eventId("task-future")
                .postId(3001L)
                .scheduledAt(scheduledAt)
                .nextAttemptAt(scheduledAt.minusSeconds(properties.getUpcomingWindowSeconds()))
                .status(ScheduledPublishStatus.PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .createdAt(dbNow)
                .updatedAt(dbNow)
                .build();
        scheduledPublishEventStore.save(record);

        scanner.scanAndEnqueue();

        verify(integrationEventPublisher, never()).publish(any());

        ScheduledPublishEventRecord stored = scheduledPublishEventStore.findByEventId("task-future").orElseThrow();
        assertEquals(ScheduledPublishStatus.PENDING, stored.getStatus());
        assertNull(stored.getTriggerEventId());
        assertNull(stored.getClaimedAt());
    }

    @Test
    @DisplayName("scanAndEnqueue：窗口内未来任务应补偿重发带 delayLevel 的消息")
    void scanShouldRepublishUpcomingTaskWithDelayLevel() {
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();

        ScheduledPublishEventRecord record = ScheduledPublishEventRecord.builder()
                .eventId("task-upcoming")
                .postId(3002L)
                .scheduledAt(dbNow.plusSeconds(30))
                .nextAttemptAt(dbNow.minusSeconds(1))
                .status(ScheduledPublishStatus.PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .createdAt(dbNow)
                .updatedAt(dbNow)
                .build();
        scheduledPublishEventStore.save(record);

        scanner.scanAndEnqueue();

        ArgumentCaptor<PostScheduleExecuteIntegrationEvent> captor =
                ArgumentCaptor.forClass(PostScheduleExecuteIntegrationEvent.class);
        verify(integrationEventPublisher).publish(captor.capture());

        PostScheduleExecuteIntegrationEvent published = captor.getValue();
        assertEquals(3002L, published.getPostId());
        assertEquals("task-upcoming", published.getScheduledPublishEventId());
        assertTrue(published.getDelayLevel() > 0);

        ScheduledPublishEventRecord stored = scheduledPublishEventStore.findByEventId("task-upcoming").orElseThrow();
        assertEquals(ScheduledPublishStatus.PENDING, stored.getStatus());
        assertNotNull(stored.getNextAttemptAt());
        assertNull(stored.getClaimedAt());
    }

    @Test
    @DisplayName("scanAndEnqueue：超时的 PROCESSING claim 可被自动回收并重发")
    void scanShouldRecoverStaleProcessingTask() {
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();

        ScheduledPublishEventRecord record = ScheduledPublishEventRecord.builder()
                .eventId("task-stale")
                .postId(2001L)
                .scheduledAt(dbNow.plusSeconds(20))
                .nextAttemptAt(dbNow.minusSeconds(10))
                .status(ScheduledPublishStatus.PROCESSING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .claimedAt(dbNow.minusSeconds(properties.getClaimTimeoutSeconds() + 5L))
                .claimedBy("old-worker")
                .createdAt(dbNow.minusMinutes(1))
                .updatedAt(dbNow.minusSeconds(properties.getClaimTimeoutSeconds() + 5L))
                .build();
        scheduledPublishEventStore.save(record);

        scanner.scanAndEnqueue();

        verify(integrationEventPublisher, times(1)).publish(any(PostScheduleExecuteIntegrationEvent.class));

        ScheduledPublishEventRecord stored = scheduledPublishEventStore.findByEventId("task-stale").orElseThrow();
        assertEquals(ScheduledPublishStatus.PENDING, stored.getStatus());
        assertNull(stored.getClaimedAt());
        assertNull(stored.getClaimedBy());
        assertNotNull(stored.getTriggerEventId());
    }

    @Test
    @DisplayName("claim：并发补偿扫描时只有一个线程能 claim 同一条任务")
    void claimShouldAllowOnlyOneThread() throws InterruptedException {
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();

        ScheduledPublishEventRecord record = ScheduledPublishEventRecord.builder()
                .eventId("task-claim")
                .postId(4001L)
                .scheduledAt(dbNow.minusMinutes(1))
                .nextAttemptAt(dbNow.minusSeconds(1))
                .status(ScheduledPublishStatus.PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .createdAt(dbNow)
                .updatedAt(dbNow)
                .build();
        scheduledPublishEventStore.save(record);

        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String worker = "worker-" + i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    if (!scheduledPublishEventStore
                            .claimCompensationBatch(dbNow, dbNow.minusSeconds(properties.getClaimTimeoutSeconds()), worker, 1)
                            .isEmpty()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(1, successCount.get());
    }

    @Test
    @DisplayName("publishScheduledIfNeeded：已发布文章应幂等返回 empty")
    void publishScheduledIfNeededShouldBeIdempotentForPublishedPost() {
        long postIdValue = System.currentTimeMillis();
        PostId postId = PostId.of(postIdValue);

        Post post = Post.createDraft(postId, UserId.of(1000L), "幂等发布测试");
        postRepository.save(post);

        jdbcTemplate.update(
                "UPDATE posts SET status = 1, published_at = CURRENT_TIMESTAMP WHERE id = ?",
                postIdValue
        );

        var result = postRepository.publishScheduledIfNeeded(postIdValue, LocalDateTime.now());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("dbNow：数据库时间与应用时间差异在合理范围内")
    void dbNowShouldBeCloseToAppNow() {
        LocalDateTime appNow = LocalDateTime.now();
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();

        long diffSeconds = Math.abs(java.time.Duration.between(appNow, dbNow).getSeconds());
        assertThat(diffSeconds).isLessThan(5L);
    }
}
