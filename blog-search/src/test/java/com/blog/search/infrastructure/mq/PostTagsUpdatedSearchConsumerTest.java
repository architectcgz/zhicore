package com.blog.search.infrastructure.mq;

import com.blog.api.client.PostServiceClient;
import com.blog.api.dto.post.PostDetailDTO;
import com.blog.api.dto.post.TagDTO;
import com.blog.api.event.post.PostTagsUpdatedEvent;
import com.blog.common.result.ApiResponse;
import com.blog.search.domain.model.PostDocument;
import com.blog.search.infrastructure.elasticsearch.PostSearchRepositoryImpl;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostTagsUpdatedSearchConsumer 测试
 * 
 * 测试 Elasticsearch 标签同步功能
 *
 * @author Blog Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostTagsUpdatedSearchConsumer 测试")
class PostTagsUpdatedSearchConsumerTest {

    @Mock
    private PostSearchRepositoryImpl postSearchRepository;

    @Mock
    private PostServiceClient postServiceClient;

    @InjectMocks
    private PostTagsUpdatedSearchConsumer consumer;

    private PostTagsUpdatedEvent event;
    private PostDetailDTO postDetail;
    private List<TagDTO> tags;

    @BeforeEach
    void setUp() {
        // 准备标签数据
        tags = Arrays.asList(
            TagDTO.builder()
                .id(1001L)
                .name("Java")
                .slug("java")
                .build(),
            TagDTO.builder()
                .id(1002L)
                .name("Spring Boot")
                .slug("spring-boot")
                .build()
        );

        // 准备文章详情
        postDetail = new PostDetailDTO();
        postDetail.setId(1L);
        postDetail.setTitle("Test Post");
        postDetail.setTags(tags);

        // 准备事件
        event = new PostTagsUpdatedEvent(
            1L,
            Arrays.asList(1001L),
            Arrays.asList(1001L, 1002L)
        );
    }

    @Test
    @DisplayName("处理标签更新事件 - 成功更新 Elasticsearch")
    void testDoHandle_Success() {
        // Given
        ApiResponse<PostDetailDTO> response = ApiResponse.success(postDetail);
        when(postServiceClient.getPostById(anyLong())).thenReturn(response);
        doNothing().when(postSearchRepository).updateTags(anyString(), anyList());

        // When
        consumer.doHandle(event);

        // Then
        ArgumentCaptor<String> postIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<PostDocument.TagInfo>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        verify(postSearchRepository).updateTags(postIdCaptor.capture(), tagsCaptor.capture());

        assertEquals("1", postIdCaptor.getValue());
        List<PostDocument.TagInfo> capturedTags = tagsCaptor.getValue();
        assertEquals(2, capturedTags.size());
        assertEquals("1001", capturedTags.get(0).getId());
        assertEquals("Java", capturedTags.get(0).getName());
        assertEquals("java", capturedTags.get(0).getSlug());
        assertEquals("1002", capturedTags.get(1).getId());
        assertEquals("Spring Boot", capturedTags.get(1).getName());
        assertEquals("spring-boot", capturedTags.get(1).getSlug());
    }

    @Test
    @DisplayName("处理标签更新事件 - 无标签")
    void testDoHandle_NoTags() {
        // Given
        PostDetailDTO postWithoutTags = new PostDetailDTO();
        postWithoutTags.setId(1L);
        postWithoutTags.setTitle("Test Post");
        postWithoutTags.setTags(null);
        ApiResponse<PostDetailDTO> response = ApiResponse.success(postWithoutTags);
        when(postServiceClient.getPostById(anyLong())).thenReturn(response);
        doNothing().when(postSearchRepository).updateTags(anyString(), isNull());

        // When
        consumer.doHandle(event);

        // Then
        ArgumentCaptor<List<PostDocument.TagInfo>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        verify(postSearchRepository).updateTags(eq("1"), tagsCaptor.capture());
        assertNull(tagsCaptor.getValue());
    }

    @Test
    @DisplayName("处理标签更新事件 - 空标签列表")
    void testDoHandle_EmptyTags() {
        // Given
        PostDetailDTO postWithEmptyTags = new PostDetailDTO();
        postWithEmptyTags.setId(1L);
        postWithEmptyTags.setTitle("Test Post");
        postWithEmptyTags.setTags(Collections.emptyList());
        ApiResponse<PostDetailDTO> response = ApiResponse.success(postWithEmptyTags);
        when(postServiceClient.getPostById(anyLong())).thenReturn(response);
        doNothing().when(postSearchRepository).updateTags(anyString(), isNull());

        // When
        consumer.doHandle(event);

        // Then
        ArgumentCaptor<List<PostDocument.TagInfo>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        verify(postSearchRepository).updateTags(eq("1"), tagsCaptor.capture());
        assertNull(tagsCaptor.getValue());
    }

    @Test
    @DisplayName("处理标签更新事件 - API 响应为 null")
    void testDoHandle_NullResponse() {
        // Given
        when(postServiceClient.getPostById(anyLong())).thenReturn(null);

        // When
        consumer.doHandle(event);

        // Then
        verify(postSearchRepository, never()).updateTags(anyString(), anyList());
    }

    @Test
    @DisplayName("处理标签更新事件 - API 响应失败")
    void testDoHandle_FailedResponse() {
        // Given
        ApiResponse<PostDetailDTO> response = ApiResponse.fail("Internal Server Error");
        when(postServiceClient.getPostById(anyLong())).thenReturn(response);

        // When
        consumer.doHandle(event);

        // Then
        verify(postSearchRepository, never()).updateTags(anyString(), anyList());
    }

    @Test
    @DisplayName("处理标签更新事件 - API 响应数据为 null")
    void testDoHandle_NullData() {
        // Given
        ApiResponse<PostDetailDTO> response = ApiResponse.success(null);
        when(postServiceClient.getPostById(anyLong())).thenReturn(response);

        // When
        consumer.doHandle(event);

        // Then
        verify(postSearchRepository, never()).updateTags(anyString(), anyList());
    }

    @Test
    @DisplayName("处理标签更新事件 - Elasticsearch 更新失败")
    void testDoHandle_ElasticsearchUpdateFailed() {
        // Given
        ApiResponse<PostDetailDTO> response = ApiResponse.success(postDetail);
        when(postServiceClient.getPostById(anyLong())).thenReturn(response);
        doThrow(new RuntimeException("Elasticsearch error")).when(postSearchRepository)
            .updateTags(anyString(), anyList());

        // When & Then
        assertThrows(RuntimeException.class, () -> consumer.doHandle(event));
    }

    @Test
    @DisplayName("处理标签更新事件 - 单个标签")
    void testDoHandle_SingleTag() {
        // Given
        List<TagDTO> singleTag = Collections.singletonList(
            TagDTO.builder()
                .id(1001L)
                .name("Java")
                .slug("java")
                .build()
        );
        PostDetailDTO postWithSingleTag = new PostDetailDTO();
        postWithSingleTag.setId(1L);
        postWithSingleTag.setTitle("Test Post");
        postWithSingleTag.setTags(singleTag);
        ApiResponse<PostDetailDTO> response = ApiResponse.success(postWithSingleTag);
        when(postServiceClient.getPostById(anyLong())).thenReturn(response);
        doNothing().when(postSearchRepository).updateTags(anyString(), anyList());

        // When
        consumer.doHandle(event);

        // Then
        ArgumentCaptor<List<PostDocument.TagInfo>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        verify(postSearchRepository).updateTags(eq("1"), tagsCaptor.capture());
        List<PostDocument.TagInfo> capturedTags = tagsCaptor.getValue();
        assertEquals(1, capturedTags.size());
        assertEquals("Java", capturedTags.get(0).getName());
    }
}
