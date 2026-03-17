package com.zhicore.content.infrastructure.scheduler;

import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.model.ScheduledPublishEventRecord;
import com.zhicore.content.application.port.store.ScheduledPublishEventStore;
import com.zhicore.content.application.service.command.ScheduledPublishDelayLevelResolver;
import com.zhicore.content.application.service.command.ScheduledPublishNextAttemptResolver;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.infrastructure.config.ScheduledPublishProperties;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 定时发布补偿扫描任务（R1）
 *
 * 目标：
 * - 保留 delay message 作为主路径，数据库扫描只承担补偿；
 * - 使用 next_attempt_at + claimed_at/claimed_by 表达统一生命周期；
 * - PROCESSING 超时任务通过 claim timeout 自动回收；
 * - 补偿扫描只负责“重新投递触发消息”，真正的发布动作仍由 MQ 消费侧执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPublishScanner {

    private final ScheduledPublishEventStore scheduledPublishEventStore;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final PostRepository postRepository;
    private final ScheduledPublishProperties properties;
    private final String compensationClaimedBy = "scheduled-compensation-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @Scheduled(
            fixedRateString = "${scheduled.publish.compensation-scan-interval-ms:1000}",
            initialDelayString = "${scheduled.publish.compensation-initial-delay-ms:1000}"
    )
    public void scanAndEnqueue() {
        LocalDateTime dbNow = scheduledPublishEventStore.dbNow();
        LocalDateTime reclaimBefore = dbNow.minusSeconds(properties.getClaimTimeoutSeconds());

        List<ScheduledPublishEventRecord> claimed = scheduledPublishEventStore.claimCompensationBatch(
                dbNow,
                reclaimBefore,
                compensationClaimedBy,
                properties.getScanBatchSize()
        );

        if (claimed.isEmpty()) {
            return;
        }

        for (ScheduledPublishEventRecord event : claimed) {
            try {
                publishCompensationTrigger(event, dbNow);
            } catch (Exception ex) {
                releaseAfterCompensationFailure(event, dbNow, ex);
            }
        }
    }

    private void publishCompensationTrigger(ScheduledPublishEventRecord event, LocalDateTime dbNow) {
        String triggerEventId = newEventId();
        Post post = postRepository.findById(event.getPostId()).orElse(null);
        Long aggregateVersion = post != null ? post.getVersion() : 0L;
        Long authorId = post != null ? post.getOwnerId().getValue() : null;

        Instant scheduledAt = event.getScheduledAt()
                .atZone(ZoneId.systemDefault())
                .toInstant();
        long remainingSeconds = Duration.between(dbNow, event.getScheduledAt()).getSeconds();
        int delayLevel = ScheduledPublishDelayLevelResolver.resolve(remainingSeconds);

        integrationEventPublisher.publish(new PostScheduleExecuteIntegrationEvent(
                triggerEventId,
                Instant.now(),
                aggregateVersion,
                event.getPostId(),
                authorId,
                scheduledAt,
                delayLevel,
                event.getEventId()
        ));

        scheduledPublishEventStore.update(
                event.withTriggerEventId(triggerEventId)
                        .withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                        .withNextAttemptAt(nextCompensationAt(dbNow, event.getScheduledAt()))
                        .withClaimedAt(null)
                        .withClaimedBy(null)
                        .withUpdatedAt(dbNow)
        );
    }

    private void releaseAfterCompensationFailure(ScheduledPublishEventRecord event, LocalDateTime dbNow, Exception ex) {
        scheduledPublishEventStore.update(
                event.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.FAILED)
                        .withNextAttemptAt(dbNow.plusSeconds(properties.getEnqueueCooldownSeconds()))
                        .withClaimedAt(null)
                        .withClaimedBy(null)
                        .withUpdatedAt(dbNow)
                        .withLastError("补偿重发触发消息失败: " + ex.getMessage())
        );
        log.warn("Scheduled publish compensation failed: taskId={}, postId={}, error={}",
                event.getEventId(), event.getPostId(), ex.getMessage(), ex);
    }

    private LocalDateTime nextCompensationAt(LocalDateTime dbNow, LocalDateTime scheduledAt) {
        return ScheduledPublishNextAttemptResolver.resolveCompensationAt(
                dbNow,
                scheduledAt,
                properties.getUpcomingWindowSeconds(),
                properties.getEnqueueCooldownSeconds()
        );
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
