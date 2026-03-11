package com.zhicore.content.infrastructure.event;

import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.event.PostDeletedEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedDomainEvent;
import com.zhicore.content.application.port.store.TagStatsCacheStore;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.persistence.pg.mapper.TagStatsEntityMyBatisMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagStatsEventHandlerTest {

    @Mock
    private TagStatsEntityMyBatisMapper tagStatsMapper;

    @Mock
    private TagStatsCacheStore tagStatsCacheStore;

    @InjectMocks
    private TagStatsEventHandler eventHandler;

    private PostCreatedDomainEvent createdEvent;
    private PostDeletedEvent deletedEvent;
    private PostTagsUpdatedDomainEvent tagsUpdatedEvent;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        createdEvent = new PostCreatedDomainEvent(
            "e1",
            now,
            PostId.of(1L),
            "t",
            "e",
            UserId.of(100L),
            "author",
            Set.of(TagId.of(1001L), TagId.of(1002L)),
            null,
            null,
            "DRAFT",
            null,
            now,
            1L
        );
        deletedEvent = new PostDeletedEvent("e2", now, PostId.of(1L), 2L);
        tagsUpdatedEvent = new PostTagsUpdatedDomainEvent(
            "e3",
            now,
            PostId.of(1L),
            Set.of(TagId.of(1001L)),
            Set.of(TagId.of(1002L), TagId.of(1003L)),
            now,
            3L
        );
    }

    @Test
    void handlePostCreated_shouldUpdateTagStats() {
        eventHandler.handlePostCreated(createdEvent);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(tagStatsMapper).batchUpsertTagStats(captor.capture());
        List<Long> ids = captor.getValue();
        assertEquals(2, ids.size());
        assertTrue(ids.contains(1001L));
        assertTrue(ids.contains(1002L));
        verify(tagStatsCacheStore).evictTagStats(ids);
        verify(tagStatsCacheStore).evictHotTags();
    }

    @Test
    void handlePostDeleted_shouldInvalidateHotTagCache() {
        eventHandler.handlePostDeleted(deletedEvent);

        verify(tagStatsCacheStore).evictHotTags();
    }

    @Test
    void handlePostTagsUpdated_shouldMergeOldAndNewTags() {
        eventHandler.handlePostTagsUpdated(tagsUpdatedEvent);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(tagStatsMapper).batchUpsertTagStats(captor.capture());
        List<Long> ids = captor.getValue();
        assertEquals(3, ids.size());
        assertTrue(ids.contains(1001L));
        assertTrue(ids.contains(1002L));
        assertTrue(ids.contains(1003L));
        verify(tagStatsCacheStore).evictTagStats(ids);
        verify(tagStatsCacheStore).evictHotTags();
    }
}
