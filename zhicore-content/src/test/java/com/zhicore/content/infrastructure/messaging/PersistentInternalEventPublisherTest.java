package com.zhicore.content.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.content.domain.event.PostContentUpdatedEvent;
import com.zhicore.content.domain.event.PostDeletedEvent;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.infrastructure.persistence.pg.entity.InternalEventTaskEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.InternalEventTaskMapper;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersistentInternalEventPublisher 测试")
class PersistentInternalEventPublisherTest {

    @Mock
    private InternalEventTaskMapper internalEventTaskMapper;

    @Mock
    private TransactionCommitSignal transactionCommitSignal;

    @Mock
    private InternalEventTaskDispatcher internalEventTaskDispatcher;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("删除事件入表时应该写入高优先级")
    void shouldPersistHighPriorityForVisibilityEvent() {
        PersistentInternalEventPublisher publisher =
                new PersistentInternalEventPublisher(
                        internalEventTaskMapper,
                        objectMapper,
                        transactionCommitSignal,
                        internalEventTaskDispatcher
                );
        PostDeletedEvent event = new PostDeletedEvent(
                "event-delete",
                Instant.parse("2026-03-14T10:00:00Z"),
                PostId.of(1L),
                Set.of(TagId.of(10L)),
                2L
        );

        publisher.publish(event);

        ArgumentCaptor<InternalEventTaskEntity> captor = ArgumentCaptor.forClass(InternalEventTaskEntity.class);
        verify(internalEventTaskMapper).insert(captor.capture());
        verify(transactionCommitSignal).afterCommit(any());
        assertEquals(InternalEventTaskEntity.PRIORITY_HIGH, captor.getValue().getPriority());
    }

    @Test
    @DisplayName("内容更新事件入表时应该写入普通优先级")
    void shouldPersistNormalPriorityForProjectionRefreshEvent() {
        PersistentInternalEventPublisher publisher =
                new PersistentInternalEventPublisher(
                        internalEventTaskMapper,
                        objectMapper,
                        transactionCommitSignal,
                        internalEventTaskDispatcher
                );
        PostContentUpdatedEvent event = new PostContentUpdatedEvent(
                "event-update",
                Instant.parse("2026-03-14T10:00:00Z"),
                PostId.of(2L),
                "updated content",
                "markdown",
                3L
        );

        publisher.publish(event);

        ArgumentCaptor<InternalEventTaskEntity> captor = ArgumentCaptor.forClass(InternalEventTaskEntity.class);
        verify(internalEventTaskMapper).insert(captor.capture());
        verify(transactionCommitSignal).afterCommit(any());
        assertEquals(InternalEventTaskEntity.PRIORITY_NORMAL, captor.getValue().getPriority());
    }
}
