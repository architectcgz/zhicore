package com.blog.post.infrastructure.event;

import com.blog.post.domain.event.PostCreatedEvent;
import com.blog.post.domain.event.PostTagsUpdatedEvent;
import com.blog.post.domain.model.Tag;
import com.blog.post.domain.repository.TagRepository;
import com.blog.post.infrastructure.mongodb.document.PostDocument;
import com.blog.post.infrastructure.mongodb.repository.PostDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * PostMongoDBSyncEventHandler 测试
 *
 * @author Blog Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostMongoDBSyncEventHandler 测试")
class PostMongoDBSyncEventHandlerTest {

    @Mock
    private PostDocumentRepository postDocumentRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private PostMongoDBSyncEventHandler eventHandler;

    private PostCreatedEvent postCreatedEvent;
    private PostTagsUpdatedEvent postTagsUpdatedEvent;
    private List<Tag> tags;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        tags = Arrays.asList(
            Tag.create(1001L, "Java", "java"),
            Tag.create(1002L, "Spring Boot", "spring-boot")
        );

        postCreatedEvent = new PostCreatedEvent(
            "1",
            "Test Post",
            "Test Content",
            "Test Excerpt",
            "100",
            "Test Author",
            Arrays.asList("1001", "1002"),
            "10",
            "Technology",
            "PUBLISHED",
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        postTagsUpdatedEvent = new PostTagsUpdatedEvent(
            "1",
            Arrays.asList("1001"),
            Arrays.asList("1001", "1002"),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("处理文章创建事件 - 成功同步到 MongoDB")
    void testHandlePostCreated_Success() {
        // Given
        when(tagRepository.findByIdIn(anyList())).thenReturn(tags);
        when(postDocumentRepository.save(any(PostDocument.class))).thenAnswer(i -> i.getArgument(0));

        // When
        eventHandler.handlePostCreated(postCreatedEvent);

        // Then
        ArgumentCaptor<PostDocument> captor = ArgumentCaptor.forClass(PostDocument.class);
        verify(postDocumentRepository).save(captor.capture());

        PostDocument savedDocument = captor.getValue();
        assertEquals("1", savedDocument.getPostId());
        assertEquals("Test Post", savedDocument.getTitle());
        assertEquals(2, savedDocument.getTags().size());
        assertEquals("Java", savedDocument.getTags().get(0).getName());
        assertEquals("java", savedDocument.getTags().get(0).getSlug());
    }

    @Test
    @DisplayName("处理文章创建事件 - 无标签")
    void testHandlePostCreated_NoTags() {
        // Given
        PostCreatedEvent eventWithoutTags = new PostCreatedEvent(
            "1",
            "Test Post",
            "Test Content",
            "Test Excerpt",
            "100",
            "Test Author",
            null,
            "10",
            "Technology",
            "PUBLISHED",
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        when(postDocumentRepository.save(any(PostDocument.class))).thenAnswer(i -> i.getArgument(0));

        // When
        eventHandler.handlePostCreated(eventWithoutTags);

        // Then
        ArgumentCaptor<PostDocument> captor = ArgumentCaptor.forClass(PostDocument.class);
        verify(postDocumentRepository).save(captor.capture());

        PostDocument savedDocument = captor.getValue();
        assertTrue(savedDocument.getTags().isEmpty());
        verify(tagRepository, never()).findByIdIn(anyList());
    }

    @Test
    @DisplayName("处理文章创建事件 - 异常不影响主流程")
    void testHandlePostCreated_ExceptionHandled() {
        // Given
        when(tagRepository.findByIdIn(anyList())).thenThrow(new RuntimeException("Database error"));

        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> eventHandler.handlePostCreated(postCreatedEvent));
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 成功更新")
    void testHandlePostTagsUpdated_Success() {
        // Given
        PostDocument existingDocument = PostDocument.builder()
            .id("mongo-id-1")
            .postId("1")
            .title("Test Post")
            .tags(Arrays.asList(
                PostDocument.TagInfo.builder()
                    .id("1001")
                    .name("Java")
                    .slug("java")
                    .build()
            ))
            .build();

        when(postDocumentRepository.findByPostId("1")).thenReturn(Optional.of(existingDocument));
        when(tagRepository.findByIdIn(anyList())).thenReturn(tags);
        when(postDocumentRepository.save(any(PostDocument.class))).thenAnswer(i -> i.getArgument(0));

        // When
        eventHandler.handlePostTagsUpdated(postTagsUpdatedEvent);

        // Then
        ArgumentCaptor<PostDocument> captor = ArgumentCaptor.forClass(PostDocument.class);
        verify(postDocumentRepository).save(captor.capture());

        PostDocument updatedDocument = captor.getValue();
        assertEquals(2, updatedDocument.getTags().size());
        assertEquals("Spring Boot", updatedDocument.getTags().get(1).getName());
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 文档不存在")
    void testHandlePostTagsUpdated_DocumentNotFound() {
        // Given
        when(postDocumentRepository.findByPostId("1")).thenReturn(Optional.empty());

        // When
        eventHandler.handlePostTagsUpdated(postTagsUpdatedEvent);

        // Then
        verify(postDocumentRepository, never()).save(any());
        verify(tagRepository, never()).findByIdIn(anyList());
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 清空标签")
    void testHandlePostTagsUpdated_ClearTags() {
        // Given
        PostDocument existingDocument = PostDocument.builder()
            .id("mongo-id-1")
            .postId("1")
            .title("Test Post")
            .tags(Arrays.asList(
                PostDocument.TagInfo.builder()
                    .id("1001")
                    .name("Java")
                    .slug("java")
                    .build()
            ))
            .build();

        PostTagsUpdatedEvent eventWithoutTags = new PostTagsUpdatedEvent(
            "1",
            Arrays.asList("1001"),
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        when(postDocumentRepository.findByPostId("1")).thenReturn(Optional.of(existingDocument));
        when(postDocumentRepository.save(any(PostDocument.class))).thenAnswer(i -> i.getArgument(0));

        // When
        eventHandler.handlePostTagsUpdated(eventWithoutTags);

        // Then
        ArgumentCaptor<PostDocument> captor = ArgumentCaptor.forClass(PostDocument.class);
        verify(postDocumentRepository).save(captor.capture());

        PostDocument updatedDocument = captor.getValue();
        assertTrue(updatedDocument.getTags().isEmpty());
    }

    @Test
    @DisplayName("处理文章标签更新事件 - 异常不影响主流程")
    void testHandlePostTagsUpdated_ExceptionHandled() {
        // Given
        when(postDocumentRepository.findByPostId("1")).thenThrow(new RuntimeException("Database error"));

        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> eventHandler.handlePostTagsUpdated(postTagsUpdatedEvent));
    }
}
