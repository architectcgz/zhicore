package com.zhicore.content.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.dispatcher.ClaimBasedDispatcher;
import com.zhicore.content.domain.event.DomainEvent;
import com.zhicore.content.domain.event.PostContentUpdatedEvent;
import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.event.PostDeletedEvent;
import com.zhicore.content.domain.event.PostMetadataUpdatedEvent;
import com.zhicore.content.domain.event.PostPublishedDomainEvent;
import com.zhicore.content.domain.event.PostPurgedEvent;
import com.zhicore.content.domain.event.PostRestoredEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedDomainEvent;
import com.zhicore.content.infrastructure.config.InternalEventDispatcherProperties;
import com.zhicore.content.infrastructure.event.PostMongoDBSyncEventHandler;
import com.zhicore.content.infrastructure.event.TagStatsEventHandler;
import com.zhicore.content.infrastructure.persistence.pg.entity.InternalEventTaskEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.InternalEventTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 内容服务内部事件任务派发器。
 *
 * <p>新的派发模型不再依赖全局分布式锁，而是通过数据库 claim + 本地 worker 并发处理：
 * - 事务提交后主动唤醒
 * - 定时任务只负责兜底补扫
 * - PROCESSING 超时任务会被重新 claim
 */
@Slf4j
@Component
public class InternalEventTaskDispatcher extends ClaimBasedDispatcher<InternalEventTaskEntity> {

    private final InternalEventTaskMapper internalEventTaskMapper;
    private final InternalEventDispatcherProperties properties;
    private final ObjectMapper objectMapper;
    private final PostMongoDBSyncEventHandler postMongoDBSyncEventHandler;
    private final TagStatsEventHandler tagStatsEventHandler;

    public InternalEventTaskDispatcher(InternalEventTaskMapper internalEventTaskMapper,
                                       InternalEventDispatcherProperties properties,
                                       @Qualifier("asyncEventExecutor") TaskExecutor taskExecutor,
                                       ObjectMapper objectMapper,
                                       PostMongoDBSyncEventHandler postMongoDBSyncEventHandler,
                                       TagStatsEventHandler tagStatsEventHandler,
                                       TransactionOperations transactionOperations) {
        super(
                "internal-event",
                properties.getWorkerCount(),
                Duration.ofSeconds(properties.getClaimTimeoutSeconds()),
                taskExecutor,
                transactionOperations
        );
        this.internalEventTaskMapper = internalEventTaskMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.postMongoDBSyncEventHandler = postMongoDBSyncEventHandler;
        this.tagStatsEventHandler = tagStatsEventHandler;
    }

    @Override
    protected long sweepIntervalMillis() {
        return properties.getScanInterval();
    }

    @Override
    protected int batchSize() {
        return properties.getBatchSize();
    }

    @Override
    protected List<InternalEventTaskEntity> claimBatch(String workerId, int limit, Duration claimTimeout) {
        Instant now = Instant.now();
        Instant reclaimBefore = now.minus(claimTimeout);
        return internalEventTaskMapper.claimDispatchable(now, reclaimBefore, workerId, limit);
    }

    @Override
    protected void handleClaimed(InternalEventTaskEntity task) {
        try {
            Class<?> eventClass = Class.forName(task.getEventType());
            DomainEvent<?> event = (DomainEvent<?>) objectMapper.readValue(task.getPayload(), eventClass);
            route(event);

            Instant now = Instant.now();
            task.setStatus(InternalEventTaskEntity.InternalEventTaskStatus.SUCCEEDED);
            task.setDispatchedAt(now);
            task.setUpdatedAt(now);
            task.setClaimedAt(null);
            task.setClaimedBy(null);
            task.setLastError(null);
            internalEventTaskMapper.updateById(task);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to dispatch internal event task: eventId=" + task.getEventId(), ex);
        }
    }

    @Override
    protected void handleFailure(InternalEventTaskEntity task, Exception exception) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        retryCount++;
        Instant now = Instant.now();
        task.setRetryCount(retryCount);
        task.setUpdatedAt(now);
        task.setLastError(exception.getMessage());
        task.setClaimedAt(null);
        task.setClaimedBy(null);

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
