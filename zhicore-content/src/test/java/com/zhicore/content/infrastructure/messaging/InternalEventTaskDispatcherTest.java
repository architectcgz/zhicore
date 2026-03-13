package com.zhicore.content.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.cache.LockKeys;
import com.zhicore.content.infrastructure.config.InternalEventDispatcherProperties;
import com.zhicore.content.infrastructure.event.PostMongoDBSyncEventHandler;
import com.zhicore.content.infrastructure.event.TagStatsEventHandler;
import com.zhicore.content.infrastructure.persistence.pg.entity.InternalEventTaskEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.InternalEventTaskMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalEventTaskDispatcher 测试")
class InternalEventTaskDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Mock
    private InternalEventTaskMapper internalEventTaskMapper;

    @Mock
    private LockManager lockManager;

    @Mock
    private LockKeys lockKeys;

    @Mock
    private PostMongoDBSyncEventHandler postMongoDBSyncEventHandler;

    @Mock
    private TagStatsEventHandler tagStatsEventHandler;

    @Test
    @DisplayName("派发创建事件时应该驱动 Mongo 与 Tag 投影")
    void shouldDispatchCreatedEvent() throws Exception {
        InternalEventDispatcherProperties properties = new InternalEventDispatcherProperties();
        InternalEventTaskDispatcher dispatcher = new InternalEventTaskDispatcher(
                internalEventTaskMapper,
                properties,
                lockManager,
                lockKeys,
                objectMapper,
                postMongoDBSyncEventHandler,
                tagStatsEventHandler,
                immediateTransactions()
        );

        PostCreatedDomainEvent event = new PostCreatedDomainEvent(
                "event-1",
                Instant.parse("2026-03-13T10:00:00Z"),
                PostId.of(1L),
                "post",
                "excerpt",
                UserId.of(2L),
                "author",
                Set.of(TagId.of(10L)),
                null,
                null,
                "PUBLISHED",
                Instant.parse("2026-03-13T10:00:00Z"),
                Instant.parse("2026-03-13T10:00:00Z"),
                1L
        );

        when(lockKeys.internalEventDispatcher()).thenReturn("lock:key");
        when(lockManager.tryLockWithWatchdog(anyString(), any())).thenReturn(true);
        when(internalEventTaskMapper.findDispatchable(any(), anyInt())).thenReturn(List.of(task(event)));

        dispatcher.dispatch();

        verify(postMongoDBSyncEventHandler).handlePostCreated(any(PostCreatedDomainEvent.class));
        verify(tagStatsEventHandler).handlePostCreated(any(PostCreatedDomainEvent.class));
        verify(internalEventTaskMapper).updateById(any(InternalEventTaskEntity.class));
        verify(lockManager).unlock("lock:key");
    }

    @Test
    @DisplayName("投影失败时应该进入 FAILED 并增加重试次数")
    void shouldMarkTaskFailedWhenProjectionThrows() throws Exception {
        InternalEventDispatcherProperties properties = new InternalEventDispatcherProperties();
        InternalEventTaskDispatcher dispatcher = new InternalEventTaskDispatcher(
                internalEventTaskMapper,
                properties,
                lockManager,
                lockKeys,
                objectMapper,
                postMongoDBSyncEventHandler,
                tagStatsEventHandler,
                immediateTransactions()
        );

        PostCreatedDomainEvent event = new PostCreatedDomainEvent(
                "event-2",
                Instant.parse("2026-03-13T10:00:00Z"),
                PostId.of(1L),
                "post",
                "excerpt",
                UserId.of(2L),
                "author",
                Set.of(TagId.of(10L)),
                null,
                null,
                "PUBLISHED",
                Instant.parse("2026-03-13T10:00:00Z"),
                Instant.parse("2026-03-13T10:00:00Z"),
                1L
        );
        InternalEventTaskEntity task = task(event);

        when(lockKeys.internalEventDispatcher()).thenReturn("lock:key");
        when(lockManager.tryLockWithWatchdog(anyString(), any())).thenReturn(true);
        when(internalEventTaskMapper.findDispatchable(any(), anyInt())).thenReturn(List.of(task));
        doThrow(new IllegalStateException("projection failed"))
                .when(postMongoDBSyncEventHandler).handlePostCreated(any(PostCreatedDomainEvent.class));

        dispatcher.dispatch();

        ArgumentCaptor<InternalEventTaskEntity> captor = ArgumentCaptor.forClass(InternalEventTaskEntity.class);
        verify(internalEventTaskMapper).updateById(captor.capture());
        assertEquals(InternalEventTaskEntity.InternalEventTaskStatus.FAILED, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getRetryCount());
    }

    private InternalEventTaskEntity task(PostCreatedDomainEvent event) throws Exception {
        InternalEventTaskEntity task = new InternalEventTaskEntity();
        task.setId(1L);
        task.setEventId(event.getEventId());
        task.setEventType(event.getClass().getName());
        task.setAggregateId(event.getPostId().getValue());
        task.setAggregateVersion(event.getAggregateVersion());
        task.setSchemaVersion(event.getSchemaVersion());
        task.setPayload(objectMapper.writeValueAsString(event));
        task.setOccurredAt(event.getOccurredAt());
        task.setRetryCount(0);
        task.setStatus(InternalEventTaskEntity.InternalEventTaskStatus.PENDING);
        task.setNextAttemptAt(Instant.now());
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return task;
    }

    private TransactionOperations immediateTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return action.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
            }
        };
    }
}
