package com.zhicore.content.infrastructure.event;

import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.domain.event.PostCreatedDomainEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedDomainEvent;
import com.zhicore.content.domain.model.ContentType;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.infrastructure.persistence.mongo.document.PostDocument;
import com.zhicore.content.infrastructure.persistence.mongo.repository.PostDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostMongoDBSyncEventHandlerTest {

    @Mock
    private PostDocumentRepository postDocumentRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private PostContentStore postContentStore;

    @InjectMocks
    private PostMongoDBSyncEventHandler eventHandler;

    private PostCreatedDomainEvent createdEvent;
    private PostTagsUpdatedDomainEvent tagsUpdatedEvent;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        createdEvent = new PostCreatedDomainEvent(
            "e1",
            now,
            PostId.of(1L),
            "Test Post",
            "Test Excerpt",
            UserId.of(100L),
            "Author",
            Set.of(TagId.of(1001L), TagId.of(1002L)),
            null,
            null,
            "PUBLISHED",
            now,
            now,
            1L
        );
        tagsUpdatedEvent = new PostTagsUpdatedDomainEvent(
            "e2",
            now,
            PostId.of(1L),
            Set.of(TagId.of(1001L)),
            Set.of(TagId.of(1001L), TagId.of(1002L)),
            now,
            2L
        );
    }

    @Test
    void handlePostCreated_shouldSaveMongoDocument() {
        List<Tag> tags = List.of(
            Tag.create(1001L, "Java", "java"),
            Tag.create(1002L, "Spring", "spring")
        );
        when(tagRepository.findByIdIn(anyList())).thenReturn(tags);
        when(postContentStore.getContent(PostId.of(1L)))
            .thenReturn(Optional.of(PostBody.create(PostId.of(1L), "markdown", ContentType.MARKDOWN)));
        when(postDocumentRepository.save(any(PostDocument.class))).thenAnswer(i -> i.getArgument(0));

        eventHandler.handlePostCreated(createdEvent);

        ArgumentCaptor<PostDocument> captor = ArgumentCaptor.forClass(PostDocument.class);
        verify(postDocumentRepository).save(captor.capture());
        PostDocument doc = captor.getValue();
        assertEquals("1", doc.getPostId());
        assertEquals("Test Post", doc.getTitle());
        assertEquals("markdown", doc.getContent());
        assertNotNull(doc.getTags());
        assertEquals(2, doc.getTags().size());
    }

    @Test
    void handlePostTagsUpdated_shouldUpdateTagList() {
        PostDocument existing = PostDocument.builder().id("m1").postId("1").tags(List.of()).build();
        List<Tag> tags = List.of(
            Tag.create(1001L, "Java", "java"),
            Tag.create(1002L, "Spring", "spring")
        );
        when(postDocumentRepository.findByPostId("1")).thenReturn(Optional.of(existing));
        when(tagRepository.findByIdIn(anyList())).thenReturn(tags);
        when(postDocumentRepository.save(any(PostDocument.class))).thenAnswer(i -> i.getArgument(0));

        eventHandler.handlePostTagsUpdated(tagsUpdatedEvent);

        ArgumentCaptor<PostDocument> captor = ArgumentCaptor.forClass(PostDocument.class);
        verify(postDocumentRepository).save(captor.capture());
        PostDocument updated = captor.getValue();
        assertTrue(updated.getTags().size() >= 2);
    }
}

