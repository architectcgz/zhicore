package com.zhicore.content.infrastructure.scheduler;

import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.repo.ScheduledPublishEventRepository;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.infrastructure.config.ScheduledPublishProperties;
import com.zhicore.content.infrastructure.persistence.pg.entity.ScheduledPublishEventEntity;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * 定时发布扫描与补扫任务（R1）
 *
 * 目标：
 * - 扫描 SCHEDULED_PENDING 且到点的事件，通过 last_enqueue_at CAS 闸门控制入队；
 * - 补扫重置超过 10 分钟的 last_enqueue_at，避免单点卡死。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPublishScanner {

    private final ScheduledPublishEventRepository scheduledPublishEventRepository;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final PostRepository postRepository;
    private final ScheduledPublishProperties properties;

    @Scheduled(fixedRate = 60_000, initialDelay = 30_000)
    public void scanAndEnqueue() {
        LocalDateTime dbNow = scheduledPublishEventRepository.dbNow();
        LocalDateTime cooldownBefore = dbNow.minusMinutes(properties.getEnqueueCooldownMinutes());

        List<ScheduledPublishEventEntity> dueEvents = scheduledPublishEventRepository.findDueScheduledPending(
                dbNow,
                cooldownBefore,
                properties.getScanBatchSize()
        );

        if (dueEvents.isEmpty()) {
            return;
        }

        for (ScheduledPublishEventEntity event : dueEvents) {
            String newEventId = newEventId();
            int affected = scheduledPublishEventRepository.casUpdateLastEnqueueAt(event, dbNow, newEventId);
            if (affected != 1) {
                continue;
            }

            Post post = postRepository.findById(event.getPostId()).orElse(null);
            Long aggregateVersion = post != null ? post.getVersion() : 0L;
            Long authorId = post != null ? post.getOwnerId().getValue() : null;

            Instant scheduledAt = event.getScheduledAt()
                    .atZone(ZoneId.systemDefault())
                    .toInstant();

            integrationEventPublisher.publish(new PostScheduleExecuteIntegrationEvent(
                    newEventId,
                    Instant.now(),
                    aggregateVersion,
                    event.getPostId(),
                    authorId,
                    scheduledAt,
                    0
            ));
        }
    }

    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    public void resetStaleGate() {
        LocalDateTime dbNow = scheduledPublishEventRepository.dbNow();
        LocalDateTime staleBefore = dbNow.minusMinutes(10);

        List<ScheduledPublishEventEntity> staleEvents = scheduledPublishEventRepository.findStaleScheduledPending(
                dbNow,
                staleBefore,
                properties.getScanBatchSize()
        );

        for (ScheduledPublishEventEntity event : staleEvents) {
            event.setLastEnqueueAt(null);
            event.setUpdatedAt(dbNow);
            event.setLastError("补扫重置 last_enqueue_at（超过 10 分钟未更新）");
            scheduledPublishEventRepository.update(event);
        }
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

