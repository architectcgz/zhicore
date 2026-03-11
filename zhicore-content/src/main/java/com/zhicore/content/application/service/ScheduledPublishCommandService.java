package com.zhicore.content.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.ForbiddenException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.model.OutboxEventRecord;
import com.zhicore.content.application.model.OutboxEventTypes;
import com.zhicore.content.application.model.ScheduledPublishEventRecord;
import com.zhicore.content.application.port.alert.ContentAlertPort;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.policy.ScheduledPublishPolicy;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.OutboxEventStore;
import com.zhicore.content.application.port.store.ScheduledPublishEventStore;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.UserId;
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

    private final PostRepository postRepository;
    private final ScheduledPublishEventStore scheduledPublishEventStore;
    private final ScheduledPublishPolicy scheduledPublishPolicy;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final ContentAlertPort alertService;
    private final OutboxEventStore outboxEventStore;
    private final ObjectMapper objectMapper;

    @Transactional
    public void schedulePublish(Long userId, Long postId, LocalDateTime scheduledAt) {
        Post post = getPostAndCheckOwnership(postId, userId);

        post.schedulePublish(scheduledAt);
        postRepository.update(post);

        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();
        long initialDelaySeconds = java.time.Duration.between(dbNow, scheduledAt).getSeconds();
        int delayLevel = calculateDelayLevelBySeconds(Math.max(0, initialDelaySeconds));

        String scheduleExecuteEventId = newEventId();
        ScheduledPublishEventRecord record = ScheduledPublishEventRecord.builder()
                .eventId(scheduleExecuteEventId)
                .postId(postId)
                .scheduledAt(scheduledAt)
                .status(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .lastEnqueueAt(dbNow)
                .createdAt(dbNow)
                .updatedAt(dbNow)
                .build();
        scheduledPublishEventStore.save(record);

        Long aggregateVersion = postRepository.findById(postId)
                .map(Post::getVersion)
                .orElse(post.getVersion());

        integrationEventPublisher.publish(new PostScheduleExecuteIntegrationEvent(
                scheduleExecuteEventId,
                Instant.now(),
                aggregateVersion,
                postId,
                userId,
                scheduledAt.atZone(ZoneId.systemDefault()).toInstant(),
                delayLevel
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
        Post post = getPostAndCheckOwnership(postId, userId);
        post.cancelSchedule();
        postRepository.update(post);
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
                0
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

        ScheduledPublishEventRecord record = scheduledPublishEventStore.findByEventId(message.getEventId())
                .or(() -> scheduledPublishEventStore.findActiveByPostId(postId))
                .orElseGet(() -> {
                    ScheduledPublishEventRecord created = ScheduledPublishEventRecord.builder()
                            .eventId(message.getEventId())
                            .postId(postId)
                            .scheduledAt(scheduledAt)
                            .status(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                            .rescheduleRetryCount(0)
                            .publishRetryCount(0)
                            .lastEnqueueAt(null)
                            .createdAt(dbNow)
                            .updatedAt(dbNow)
                            .build();
                    scheduledPublishEventStore.save(created);
                    return created;
                });

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

            int delayLevel = calculateDelayLevelBySeconds(Math.max(1, targetDelaySeconds));
            String newEventId = newEventId();

            record = record.withEventId(newEventId)
                    .withRescheduleRetryCount(currentRetry + 1)
                    .withLastEnqueueAt(dbNow)
                    .withUpdatedAt(dbNow)
                    .withLastError("未到点，重入队 remainingSeconds=" + remainingSeconds + ", delayLevel=" + delayLevel);
            scheduledPublishEventStore.update(record);

            Long aggregateVersion = postRepository.findById(record.getPostId())
                    .map(Post::getVersion)
                    .orElse(0L);

            integrationEventPublisher.publish(new PostScheduleExecuteIntegrationEvent(
                    newEventId,
                    Instant.now(),
                    aggregateVersion,
                    record.getPostId(),
                    message.getAuthorId(),
                    scheduledAt.atZone(ZoneId.systemDefault()).toInstant(),
                    delayLevel
            ));
            return;
        }

        record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.SCHEDULED_PENDING)
                .withUpdatedAt(dbNow)
                .withLastError("未到点且重入队达到上限，转入 SCHEDULED_PENDING 由扫描任务兜底");
        scheduledPublishEventStore.update(record);
    }

    private void handleDue(ScheduledPublishEventRecord record, LocalDateTime dbNow, LocalDateTime scheduledAt) {
        try {
            Optional<Long> newVersion = postRepository.publishScheduledIfNeeded(record.getPostId(), dbNow);
            if (newVersion.isPresent()) {
                record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.PUBLISHED)
                        .withUpdatedAt(dbNow)
                        .withLastError(null);
                scheduledPublishEventStore.update(record);

                integrationEventPublisher.publish(new PostPublishedIntegrationEvent(
                        newEventId(),
                        Instant.now(),
                        record.getPostId(),
                        dbNow.atZone(ZoneId.systemDefault()).toInstant(),
                        newVersion.get()
                ));
                return;
            }

            Post existing = postRepository.findById(record.getPostId()).orElse(null);
            if (existing == null) {
                record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.FAILED)
                        .withUpdatedAt(dbNow)
                        .withLastError("发布 no-op 且文章不存在");
                scheduledPublishEventStore.update(record);
                return;
            }

            if (existing.getStatus() == PostStatus.PUBLISHED) {
                record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.PUBLISHED)
                        .withUpdatedAt(dbNow)
                        .withLastError(null);
                scheduledPublishEventStore.update(record);
                return;
            }

            record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.FAILED)
                    .withUpdatedAt(dbNow)
                    .withLastError("发布 no-op 且文章状态非法: " + existing.getStatus());
            scheduledPublishEventStore.update(record);
        } catch (Exception e) {
            int currentRetry = record.getPublishRetryCount() != null ? record.getPublishRetryCount() : 0;
            if (currentRetry < scheduledPublishPolicy.maxPublishRetries()) {
                long backoffMinutes = 1L << Math.min(currentRetry, 20);
                long targetDelaySeconds = Math.min(backoffMinutes * 60, scheduledPublishPolicy.maxDelayMinutes() * 60L);
                int delayLevel = calculateDelayLevelBySeconds(Math.max(1, targetDelaySeconds));

                String newEventId = newEventId();
                record = record.withEventId(newEventId)
                        .withPublishRetryCount(currentRetry + 1)
                        .withLastEnqueueAt(dbNow)
                        .withUpdatedAt(dbNow)
                        .withLastError("发布失败重试: " + e.getMessage());
                scheduledPublishEventStore.update(record);

                Long aggregateVersion = postRepository.findById(record.getPostId())
                        .map(Post::getVersion)
                        .orElse(0L);

                integrationEventPublisher.publish(new PostScheduleExecuteIntegrationEvent(
                        newEventId,
                        Instant.now(),
                        aggregateVersion,
                        record.getPostId(),
                        null,
                        scheduledAt.atZone(ZoneId.systemDefault()).toInstant(),
                        delayLevel
                ));
                return;
            }

            record = record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.FAILED)
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
                .retryCount(0)
                .status(OutboxEventRecord.OutboxStatus.PENDING)
                .build();

        outboxEventStore.save(entity);
        log.warn("Scheduled publish DLQ outbox event created: eventId={}, postId={}", entity.getEventId(), postId);
    }

    private Post getPostAndCheckOwnership(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));

        if (!post.isOwnedBy(UserId.of(userId))) {
            throw new ForbiddenException("无权操作此文章");
        }

        return post;
    }

    private int calculateDelayLevelBySeconds(long delaySeconds) {
        if (delaySeconds <= 1) return 1;
        if (delaySeconds <= 5) return 2;
        if (delaySeconds <= 10) return 3;
        if (delaySeconds <= 30) return 4;
        if (delaySeconds <= 60) return 5;
        if (delaySeconds <= 120) return 6;
        if (delaySeconds <= 180) return 7;
        if (delaySeconds <= 240) return 8;
        if (delaySeconds <= 300) return 9;
        if (delaySeconds <= 360) return 10;
        if (delaySeconds <= 420) return 11;
        if (delaySeconds <= 480) return 12;
        if (delaySeconds <= 540) return 13;
        if (delaySeconds <= 600) return 14;
        if (delaySeconds <= 1200) return 15;
        if (delaySeconds <= 1800) return 16;
        if (delaySeconds <= 3600) return 17;
        return 18;
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
