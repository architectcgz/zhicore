package com.zhicore.search.infrastructure.mq;

import com.zhicore.api.dto.post.PostDetailDTO;
import com.zhicore.api.dto.post.TagDTO;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.api.event.post.PostUpdatedEvent;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.search.domain.model.PostDocument;
import com.zhicore.search.domain.repository.PostSearchRepository;
import com.zhicore.search.infrastructure.feign.PostServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostUpdatedSearchConsumer 测试")
class PostUpdatedSearchConsumerTest {

    @Mock
    private PostSearchRepository postSearchRepository;

    @Mock
    private PostServiceClient postServiceClient;

    private PostUpdatedSearchConsumer consumer;
    private PostUpdatedEvent event;

    @BeforeEach
    void setUp() {
        consumer = new PostUpdatedSearchConsumer(null, postSearchRepository, postServiceClient);
        event = new PostUpdatedEvent(1L, "Updated Title", "Updated Content", "Updated Excerpt", List.of("java"));
    }

    @Test
    @DisplayName("处理更新事件 - 索引存在时执行局部更新")
    void testDoHandle_PartialUpdate() {
        when(postSearchRepository.findById("1")).thenReturn(Optional.of(new PostDocument()));

        consumer.doHandle(event);

        verify(postSearchRepository).partialUpdate("1", "Updated Title", "Updated Content", "Updated Excerpt");
        verify(postSearchRepository, never()).index(any());
    }

    @Test
    @DisplayName("处理更新事件 - 索引缺失时回源重建")
    void testDoHandle_RebuildMissingIndex() {
        when(postSearchRepository.findById("1")).thenReturn(Optional.empty());
        when(postServiceClient.getPostById(anyLong())).thenReturn(ApiResponse.success(buildPostDetail()));

        consumer.doHandle(event);

        ArgumentCaptor<PostDocument> documentCaptor = ArgumentCaptor.forClass(PostDocument.class);
        verify(postSearchRepository).index(documentCaptor.capture());
        verify(postSearchRepository, never()).partialUpdate(anyString(), any(), any(), any());
        assertEquals("1", documentCaptor.getValue().getId());
        assertEquals("Updated Title", documentCaptor.getValue().getTitle());
    }

    @Test
    @DisplayName("处理更新事件 - 回源发现文章不存在时跳过")
    void testDoHandle_SourceMissing() {
        when(postSearchRepository.findById("1")).thenReturn(Optional.empty());
        when(postServiceClient.getPostById(anyLong()))
            .thenReturn(ApiResponse.fail(ResultCode.NOT_FOUND, "文章不存在"));

        consumer.doHandle(event);

        verify(postSearchRepository, never()).index(any());
        verify(postSearchRepository, never()).partialUpdate(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("处理更新事件 - 回源失败时抛出异常重试")
    void testDoHandle_SourceFailure() {
        when(postSearchRepository.findById("1")).thenReturn(Optional.empty());
        when(postServiceClient.getPostById(anyLong()))
            .thenReturn(ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用"));

        assertThrows(IllegalStateException.class, () -> consumer.doHandle(event));
        verify(postSearchRepository, never()).index(any());
    }

    private PostDetailDTO buildPostDetail() {
        UserSimpleDTO author = new UserSimpleDTO();
        author.setId(10L);
        author.setNickname("author");

        PostDetailDTO post = new PostDetailDTO();
        post.setId(1L);
        post.setTitle("Updated Title");
        post.setRaw("Updated Content");
        post.setExcerpt("Updated Excerpt");
        post.setAuthor(author);
        post.setTags(List.of(TagDTO.builder().id(1001L).name("Java").slug("java").build()));
        return post;
    }
}
