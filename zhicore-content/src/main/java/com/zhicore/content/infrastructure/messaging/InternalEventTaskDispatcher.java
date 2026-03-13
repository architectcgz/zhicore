package com.zhicore.content.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.content.domain.event.DomainEvent;
import com.zhicore.content.domain.event.PostContentUpdatedEvent;
import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.event.PostDeletedEvent;
import com.zhicore.content.domain.event.PostMetadataUpdatedEvent;
import com.zhicore.content.domain.event.PostPublishedDomainEvent;
import com.zhicore.content.domain.event.PostPurgedEvent;
import com.zhicore.content.domain.event.PostRestoredEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedDomainEvent;
import com.zhicore.content.infrastructure.cache.LockKeys;
import com.zhicore.content.infrastructure.config.InternalEventDispatcherProperties;
import com.zhicore.content.infrastructure.event.PostMongoDBSyncEventHandler;
import com.zhicore.content.infrastructure.event.TagStatsEventHandler;
import com.zhicore.content.infrastructure.persistence.pg.entity.InternalEventTaskEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.InternalEventTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 内容服务内部事件任务派发器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalEventTaskDispatcher implements SchedulingConfigurer {

    private static final Duration LOCK_WAIT_TIME = Duration.ZERO;

    private final InternalEventTaskMapper internalEventTaskMapper;
    private final InternalEventDispatcherProperties properties;
    private final LockManager lockManager;
    private final LockKeys lockKeys;
    private final ObjectMapper objectMapper;
    private final PostMongoDBSyncEventHandler postMongoDBSyncEventHandler;
    private final TagStatsEventHandler tagStatsEventHandler;
    private final TransactionOperations transactionOperations;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(this::dispatch, Duration.ofMillis(properties.getScanInterval()));
    }

    public void dispatch() {
        String lockKey = lockKeys.internalEventDispatcher();
        boolean locked = lockManager.tryLockWithWatchdog(lockKey, LOCK_WAIT_TIME);
        if (!locked) {
            return;
        }

        try {
            List<InternalEventTaskEntity> tasks = internalEventTaskMapper.findDispatchable(Instant.now(), properties.getBatchSize());
            for (InternalEventTaskEntity task : tasks) {
                try {
                    transactionOperations.executeWithoutResult(status -> dispatchSingle(task));
                } catch (Exception e) {
                    transactionOperations.executeWithoutResult(status -> markFailure(task, e));
                }
            }
        } finally {
            try {
                lockManager.unlock(lockKey);
            } catch (Exception e) {
                log.error("Failed to unlock internal event dispatcher", e);
            }
        }
    }

    protected void dispatchSingle(InternalEventTaskEntity task) {
        try {
            Class<?> eventClass = Class.forName(task.getEventType());
            DomainEvent<?> event = (DomainEvent<?>) objectMapper.readValue(task.getPayload(), eventClass);
            route(event);

            Instant now = Instant.now();
            task.setStatus(InternalEventTaskEntity.InternalEventTaskStatus.DISPATCHED);
            task.setDispatchedAt(now);
            task.setUpdatedAt(now);
            task.setLastError(null);
            internalEventTaskMapper.updateById(task);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to dispatch internal event task: eventId=" + task.getEventId(), e);
        }
    }

    protected void markFailure(InternalEventTaskEntity task, Exception exception) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        retryCount++;
        Instant now = Instant.now();
        task.setRetryCount(retryCount);
        task.setUpdatedAt(now);
        task.setLastError(exception.getMessage());

        if (retryCount >= properties.getMaxRetry()) {
            task.setStatus(InternalEventTaskEntity.InternalEventTaskStatus.DEAD);
            task.setNextAttemptAt(now);
            log.error("Internal event task dead after retries: eventId={}, eventType={}",
                    task.getEventId(), task.getEventType(), exception);
        } else {
            task.setStatus(InternalEventTaskEntity.InternalEventTaskStatus.FAILED);
            task.setNextAttemptAt(now.plusSeconds(backoffSeconds(retryCount)));
            log.warn("Internal event task failed, will retry: eventId={}, retryCount={}",
                    task.getEventId(), retryCount, exception);
        }
        internalEventTaskMapper.updateById(task);
    }

    private void route(DomainEvent<?> event) {
        if (event instanceof PostCreatedDomainEvent createdEvent) {
            postMongoDBSyncEventHandler.handlePostCreated(createdEvent);
            tagStatsEventHandler.handlePostCreated(createdEvent);
            return;
        }
        if (event instanceof PostTagsUpdatedDomainEvent tagsUpdatedEvent) {
            postMongoDBSyncEventHandler.handlePostTagsUpdated(tagsUpdatedEvent);
            tagStatsEventHandler.handlePostTagsUpdated(tagsUpdatedEvent);
            return;
        }
        if (event instanceof PostDeletedEvent deletedEvent) {
            postMongoDBSyncEventHandler.handlePostDeleted(deletedEvent);
            tagStatsEventHandler.handlePostDeleted(deletedEvent);
            return;
        }
        if (event instanceof PostRestoredEvent restoredEvent) {
            postMongoDBSyncEventHandler.handlePostRestored(restoredEvent);
            tagStatsEventHandler.handlePostRestored(restoredEvent);
            return;
        }
        if (event instanceof PostPurgedEvent purgedEvent) {
            postMongoDBSyncEventHandler.handlePostPurged(purgedEvent);
            tagStatsEventHandler.handlePostPurged(purgedEvent);
            return;
        }
        if (event instanceof PostPublishedDomainEvent publishedEvent) {
            postMongoDBSyncEventHandler.handlePostPublished(publishedEvent);
            return;
        }
        if (event instanceof PostContentUpdatedEvent contentUpdatedEvent) {
            postMongoDBSyncEventHandler.handlePostContentUpdated(contentUpdatedEvent);
            return;
        }
        if (event instanceof PostMetadataUpdatedEvent metadataUpdatedEvent) {
            postMongoDBSyncEventHandler.handlePostMetadataUpdated(metadataUpdatedEvent);
            return;
        }

        throw new IllegalArgumentException("Unsupported internal event type: " + event.getClass().getName());
    }

    private long backoffSeconds(int retryCount) {
        long value = 1L << Math.min(retryCount - 1, 20);
        return Math.min(value, properties.getMaxBackoffSeconds());
    }
}
