package com.zhicore.content.application.service.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.content.application.model.OutboxEventRecord;
import com.zhicore.content.application.model.OutboxEventTypes;
import com.zhicore.content.application.model.ScheduledPublishEventRecord;
import com.zhicore.content.application.port.alert.ContentAlertPort;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.policy.ScheduledPublishPolicy;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.OutboxEventStore;
import com.zhicore.content.application.port.store.ScheduledPublishEventStore;
import com.zhicore.content.application.service.OwnedPostLoadService;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.config.ScheduledPublishProperties;
import com.zhicore.content.infrastructure.messaging.OutboxDispatchTrigger;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import com.zhicore.integration.messaging.post.PostScheduledIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 定时发布写服务。
 *
 * 收口定时发布编排、重试、补偿与 DLQ 逻辑，
 * 避免 PostWriteService 同时承担文章写入与异步调度职责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledPublishCommandService {

    private final OwnedPostLoadService ownedPostLoadService;
    private final PostRepository postRepository;
    private final ScheduledPublishEventStore scheduledPublishEventStore;
    private final ScheduledPublishPolicy scheduledPublishPolicy;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final ContentAlertPort alertService;
    private final OutboxEventStore outboxEventStore;
    private final ObjectMapper objectMapper;
    private final TransactionCommitSignal transactionCommitSignal;
    private final OutboxDispatchTrigger outboxDispatchTrigger;
    private final ScheduledPublishProperties scheduledPublishProperties;
    private final String executionClaimedByPrefix = "scheduled-consumer-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @Transactional
    public void schedulePublish(Long userId, Long postId, LocalDateTime scheduledAt) {
        Post post = ownedPostLoadService.load(postId, userId);

        post.schedulePublish(scheduledAt);
        postRepository.update(post);

        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();
        scheduledPublishEventStore.markTerminalByPostId(
                postId,
                ScheduledPublishEventRecord.ScheduledPublishStatus.SUCCEEDED,
                dbNow,
                "被新的定时发布配置覆盖"
        );
        long initialDelaySeconds = java.time.Duration.between(dbNow, scheduledAt).getSeconds();
        int delayLevel = ScheduledPublishDelayLevelResolver.resolve(Math.max(0, initialDelaySeconds));

        String scheduledPublishEventId = newEventId();
        String scheduleExecuteTriggerEventId = newEventId();
        ScheduledPublishEventRecord record = ScheduledPublishEventRecord.builder()
                .eventId(scheduledPublishEventId)
                .triggerEventId(scheduleExecuteTriggerEventId)
                .postId(postId)
                .scheduledAt(scheduledAt)
                .status(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                .nextAttemptAt(nextCompensationAt(dbNow, scheduledAt))
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .createdAt(dbNow)
                .updatedAt(dbNow)
                .build();
        scheduledPublishEventStore.save(record);

        Long aggregateVersion = postRepository.findById(postId)
                .map(Post::getVersion)
                .orElse(post.getVersion());

        integrationEventPublisher.publish(new PostScheduleExecuteIntegrationEvent(
                scheduleExecuteTriggerEventId,
                Instant.now(),
                aggregateVersion,
                postId,
                userId,
                scheduledAt.atZone(ZoneId.systemDefault()).toInstant(),
                delayLevel,
                scheduledPublishEventId
        ));

        integrationEventPublisher.publish(new PostScheduledIntegrationEvent(
                newEventId(),
                Instant.now(),
                aggregateVersion,
                postId,
                userId,
                scheduledAt.atZone(ZoneId.systemDefault()).toInstant()
        ));

        log.info("Post scheduled: postId={}, userId={}, scheduledAt={}", postId, userId, scheduledAt);
    }

    @Transactional
    public void cancelSchedule(Long userId, Long postId) {
        Post post = ownedPostLoadService.load(postId, userId);
        post.cancelSchedule();
        postRepository.update(post);
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();
        scheduledPublishEventStore.markTerminalByPostId(
                postId,
                ScheduledPublishEventRecord.ScheduledPublishStatus.SUCCEEDED,
                dbNow,
                null
        );
        log.info("Post schedule cancelled: postId={}, userId={}", postId, userId);
    }

    @Transactional
    public void executeScheduledPublish(Long postId) {
        PostScheduleExecuteIntegrationEvent synthetic = new PostScheduleExecuteIntegrationEvent(
                newEventId(),
                Instant.now(),
                0L,
                postId,
                null,
                Instant.now(),
                0,
                null
        );
        consumeScheduledPublish(synthetic);
    }

    /**
     * 消费“定时发布执行”事件。
     *
     * 关键点：
     * - 必须使用数据库时间做门禁
     * - 发布操作必须为单条条件更新
     * - 未到点需按退避策略重入队
     */
    @Transactional
    public void consumeScheduledPublish(PostScheduleExecuteIntegrationEvent message) {
        Long postId = message.getPostId();

        LocalDateTime appNow = LocalDateTime.now();
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();
        LocalDateTime scheduledAt = LocalDateTime.ofInstant(message.getScheduledAt(), ZoneId.systemDefault());

        ScheduledPublishEventRecord record = resolveScheduledPublishRecord(message, postId, scheduledAt, dbNow);
        if (record == null || isTerminal(record)) {
            log.info("Skip scheduled publish consume because task already settled: postId={}, taskId={}, triggerEventId={}, status={}",
                    postId,
                    message.getScheduledPublishEventId(),
                    message.getEventId(),
                    record == null ? null : record.getStatus());
            return;
        }

        record = scheduledPublishEventStore.claimForConsumption(
                        record.getEventId(),
                        dbNow,
                        dbNow.minusSeconds(scheduledPublishProperties.getClaimTimeoutSeconds()),
                        currentExecutionClaimedBy()
                )
                .orElse(null);
        if (record == null) {
            log.info("Skip scheduled publish consume because task cannot be claimed: postId={}, taskId={}, triggerEventId={}",
                    postId,
                    message.getScheduledPublishEventId(),
                    message.getEventId());
            return;
        }

        LocalDateTime effectiveScheduledAt = record.getScheduledAt();
        if (effectiveScheduledAt == null) {
            effectiveScheduledAt = scheduledAt;
        }

        log.info("Consume scheduled publish: postId={}, scheduledAt={}, appNow={}, dbNow={}, rescheduleRetry={}, publishRetry={}, status={}",
                postId,
                effectiveScheduledAt,
                appNow,
                dbNow,
                record.getRescheduleRetryCount(),
                record.getPublishRetryCount(),
                record.getStatus());

        if (dbNow.isBefore(effectiveScheduledAt)) {
            handleNotDue(record, dbNow, effectiveScheduledAt, message);
            return;
        }

        handleDue(record, dbNow, effectiveScheduledAt);
    }

    private void handleNotDue(
            ScheduledPublishEventRecord record,
            LocalDateTime dbNow,
            LocalDateTime scheduledAt,
            PostScheduleExecuteIntegrationEvent message
    ) {
        long remainingSeconds = java.time.Duration.between(dbNow, scheduledAt).getSeconds();
        int currentRetry = record.getRescheduleRetryCount() != null ? record.getRescheduleRetryCount() : 0;

        if (currentRetry < scheduledPublishPolicy.maxRescheduleRetries()) {
            long backoffMinutes = 1L << Math.min(currentRetry, 20);
            long targetDelaySeconds = Math.min(
                    remainingSeconds,
                    Math.min(backoffMinutes * 60, scheduledPublishPolicy.maxDelayMinutes() * 60L)
            );

            int delayLevel = ScheduledPublishDelayLevelResolver.resolve(Math.max(1, targetDelaySeconds));
            String triggerEventId = newEventId();

            record = record.withTriggerEventId(triggerEventId)
                    .withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                    .withNextAttemptAt(nextCompensationAt(dbNow, scheduledAt))
                    .withRescheduleRetryCount(currentRetry + 1)
                    .withClaimedAt(null)
                    .withClaimedBy(null)
                    .withUpdatedAt(dbNow)
                    .withLastError("未到点，重入队 remainingSeconds=" + remainingSeconds + ", delayLevel=" + delayLevel);
            scheduledPublishEventStore.update(record);

            Long aggregateVersion = postRepository.findById(record.getPostId())
                    .map(Post::getVersion)
                    .orElse(0L);

            integrationEventPublisher.publish(new PostScheduleExecuteIntegrationEvent(
                    triggerEventId,
                    Instant.now(),
                    aggregateVersion,
                    record.getPostId(),
                    message.getAuthorId(),
                    scheduledAt.atZone(ZoneId.systemDefault()).toInstant(),
                    delayLevel,
                    record.getEventId()
            ));
            return;
        }

        record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                .withNextAttemptAt(nextCompensationAt(dbNow, scheduledAt))
                .withClaimedAt(null)
                .withClaimedBy(null)
                .withUpdatedAt(dbNow)
                .withLastError("未到点且重入队达到上限，等待补偿扫描接管");
        scheduledPublishEventStore.update(record);
    }

    private void handleDue(ScheduledPublishEventRecord record, LocalDateTime dbNow, LocalDateTime scheduledAt) {
        try {
            Long postId = record.getPostId();
            Optional<Long> newVersion = postRepository.publishScheduledIfNeeded(postId, dbNow);
            if (newVersion.isPresent()) {
                Post publishedPost = postRepository.findById(postId)
                        .orElseThrow(() -> new IllegalStateException("定时发布成功后文章不存在: " + postId));
                record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.SUCCEEDED)
                        .withNextAttemptAt(dbNow)
                        .withClaimedAt(null)
                        .withClaimedBy(null)
                        .withUpdatedAt(dbNow)
                        .withLastError(null);
                scheduledPublishEventStore.update(record);

                integrationEventPublisher.publish(new PostPublishedIntegrationEvent(
                        newEventId(),
                        Instant.now(),
                        postId,
                        publishedPost.getOwnerId().getValue(),
                        publishedPost.getTitle(),
                        publishedPost.getExcerpt(),
                        dbNow.atZone(ZoneId.systemDefault()).toInstant(),
                        newVersion.get()
                ));
                return;
            }

            Post existing = postRepository.findById(record.getPostId()).orElse(null);
            if (existing == null) {
                record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.DEAD)
                        .withNextAttemptAt(dbNow)
                        .withClaimedAt(null)
                        .withClaimedBy(null)
                        .withUpdatedAt(dbNow)
                        .withLastError("发布 no-op 且文章不存在");
                scheduledPublishEventStore.update(record);
                return;
            }

            if (existing.getStatus() == PostStatus.PUBLISHED) {
                record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.SUCCEEDED)
                        .withNextAttemptAt(dbNow)
                        .withClaimedAt(null)
                        .withClaimedBy(null)
                        .withUpdatedAt(dbNow)
                        .withLastError(null);
                scheduledPublishEventStore.update(record);
                return;
            }

            if (isCancelledNoOp(existing)) {
                record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.SUCCEEDED)
                        .withNextAttemptAt(dbNow)
                        .withClaimedAt(null)
                        .withClaimedBy(null)
                        .withUpdatedAt(dbNow)
                        .withLastError(null);
                scheduledPublishEventStore.update(record);
                return;
            }

            record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.DEAD)
                    .withNextAttemptAt(dbNow)
                    .withClaimedAt(null)
                    .withClaimedBy(null)
                    .withUpdatedAt(dbNow)
                    .withLastError("发布 no-op 且文章状态非法: " + existing.getStatus());
            scheduledPublishEventStore.update(record);
        } catch (Exception e) {
            int currentRetry = record.getPublishRetryCount() != null ? record.getPublishRetryCount() : 0;
            if (currentRetry < scheduledPublishPolicy.maxPublishRetries()) {
                record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.FAILED)
                        .withNextAttemptAt(nextPublishRetryAt(dbNow, currentRetry + 1))
                        .withPublishRetryCount(currentRetry + 1)
                        .withClaimedAt(null)
                        .withClaimedBy(null)
                        .withUpdatedAt(dbNow)
                        .withLastError("发布失败等待重试: " + e.getMessage());
                scheduledPublishEventStore.update(record);
                return;
            }

            record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.DEAD)
                    .withNextAttemptAt(dbNow)
                    .withClaimedAt(null)
                    .withClaimedBy(null)
                    .withUpdatedAt(dbNow)
                    .withLastError("发布失败且达到重试上限: " + e.getMessage());
            scheduledPublishEventStore.update(record);

            try {
                alertService.alertScheduledPublishFailedAfterRetries(
                        record.getPostId(),
                        record.getLastError(),
                        record.getPublishRetryCount()
                );
            } catch (Exception alertEx) {
                log.error("Failed to send scheduled publish failure alert: postId={}", record.getPostId(), alertEx);
            }

            try {
                emitScheduledPublishDlqEvent(record, scheduledAt, e);
            } catch (Exception dlqEx) {
                log.error("Failed to emit scheduled publish DLQ outbox event: postId={}", record.getPostId(), dlqEx);
            }

            log.error("Scheduled publish failed after retries: postId={}, error={}", record.getPostId(), e.getMessage(), e);
        }
    }

    private ScheduledPublishEventRecord resolveScheduledPublishRecord(PostScheduleExecuteIntegrationEvent message,
                                                                      Long postId,
                                                                      LocalDateTime scheduledAt,
                                                                      LocalDateTime dbNow) {
        Optional<ScheduledPublishEventRecord> existing = Optional.ofNullable(message.getScheduledPublishEventId())
                .flatMap(scheduledPublishEventStore::findByEventId)
                .or(() -> scheduledPublishEventStore.findByTriggerEventId(message.getEventId()))
                .or(() -> scheduledPublishEventStore.findActiveByPostId(postId));

        if (existing.isPresent()) {
            return existing.get();
        }

        // 兼容旧消息或脏数据场景：如果没有权威记录，则补建一条可 claim 的任务记录。
        ScheduledPublishEventRecord created = ScheduledPublishEventRecord.builder()
                .eventId(message.getScheduledPublishEventId() != null ? message.getScheduledPublishEventId() : newEventId())
                .triggerEventId(message.getEventId())
                .postId(postId)
                .scheduledAt(scheduledAt)
                .nextAttemptAt(nextCompensationAt(dbNow, scheduledAt))
                .status(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .createdAt(dbNow)
                .updatedAt(dbNow)
                .build();
        scheduledPublishEventStore.save(created);
        return created;
    }

    private boolean isTerminal(ScheduledPublishEventRecord record) {
        return record.getStatus() == ScheduledPublishEventRecord.ScheduledPublishStatus.SUCCEEDED
                || record.getStatus() == ScheduledPublishEventRecord.ScheduledPublishStatus.DEAD;
    }

    private boolean isCancelledNoOp(Post existing) {
        return existing.getStatus() == PostStatus.DRAFT && existing.getScheduledAt() == null;
    }

    private LocalDateTime nextCompensationAt(LocalDateTime dbNow, LocalDateTime scheduledAt) {
        return ScheduledPublishNextAttemptResolver.resolveCompensationAt(
                dbNow,
                scheduledAt,
                scheduledPublishProperties.getUpcomingWindowSeconds(),
                scheduledPublishProperties.getEnqueueCooldownSeconds()
        );
    }

    private LocalDateTime nextPublishRetryAt(LocalDateTime dbNow, int retryCount) {
        long backoffMinutes = 1L << Math.min(retryCount - 1, 20);
        long delaySeconds = Math.min(backoffMinutes * 60, scheduledPublishPolicy.maxDelayMinutes() * 60L);
        return dbNow.plusSeconds(delaySeconds);
    }

    private String currentExecutionClaimedBy() {
        return executionClaimedByPrefix + "-" + Thread.currentThread().getName();
    }

    private void emitScheduledPublishDlqEvent(ScheduledPublishEventRecord record, LocalDateTime scheduledAt, Exception e)
            throws Exception {
        if (record == null || record.getPostId() == null) {
            return;
        }

        Long postId = record.getPostId();
        Long aggregateVersion = postRepository.findById(postId)
                .map(Post::getVersion)
                .orElse(0L);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("postId", postId);
        payload.put("scheduledTime", scheduledAt != null
                ? scheduledAt.atZone(ZoneId.systemDefault()).toInstant().toString()
                : null);
        payload.put("failReason", e != null ? e.getMessage() : null);
        payload.put("retryCount", record.getPublishRetryCount());
        payload.put("lastError", record.getLastError());

        Instant now = Instant.now();
        OutboxEventRecord entity = OutboxEventRecord.builder()
                .eventId(newEventId())
                .eventType(OutboxEventTypes.SCHEDULED_PUBLISH_DLQ)
                .aggregateId(postId)
                .aggregateVersion(aggregateVersion)
                .schemaVersion(1)
                .payload(objectMapper.writeValueAsString(payload))
                .occurredAt(now)
                .createdAt(now)
                .updatedAt(now)
                .nextAttemptAt(now)
                .retryCount(0)
                .status(OutboxEventRecord.OutboxStatus.PENDING)
                .build();

        outboxEventStore.save(entity);
        transactionCommitSignal.afterCommit(outboxDispatchTrigger::signal);
        log.warn("Scheduled publish DLQ outbox event created: eventId={}, postId={}", entity.getEventId(), postId);
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
