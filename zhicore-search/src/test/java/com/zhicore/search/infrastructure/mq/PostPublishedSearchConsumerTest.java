package com.zhicore.search.infrastructure.mq;

import com.zhicore.api.dto.post.PostDetailDTO;
import com.zhicore.api.dto.post.TagDTO;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.api.event.post.PostPublishedEvent;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostPublishedSearchConsumer 测试")
class PostPublishedSearchConsumerTest {

    @Mock
    private PostSearchRepository postSearchRepository;

    @Mock
    private PostServiceClient postServiceClient;

    private PostPublishedSearchConsumer consumer;
    private PostPublishedEvent event;

    @BeforeEach
    void setUp() {
        consumer = new PostPublishedSearchConsumer(null, postSearchRepository, postServiceClient);
        event = new PostPublishedEvent(1L, 10L, "Test Post", "excerpt", List.of("java"));
    }

    @Test
    @DisplayName("处理发布事件 - 成功索引全文")
    void testDoHandle_Success() {
        when(postServiceClient.getPostById(anyLong())).thenReturn(ApiResponse.success(buildPostDetail()));

        consumer.doHandle(event);

        ArgumentCaptor<PostDocument> documentCaptor = ArgumentCaptor.forClass(PostDocument.class);
        verify(postSearchRepository).index(documentCaptor.capture());
        PostDocument document = documentCaptor.getValue();
        assertEquals("1", document.getId());
        assertEquals("Test Post", document.getTitle());
        assertEquals("author", document.getAuthorName());
        assertEquals(2, document.getTags().size());
    }

    @Test
    @DisplayName("处理发布事件 - 文章不存在时跳过")
    void testDoHandle_PostMissing() {
        when(postServiceClient.getPostById(anyLong()))
            .thenReturn(ApiResponse.fail(ResultCode.NOT_FOUND, "文章不存在"));

        consumer.doHandle(event);

        verify(postSearchRepository, never()).index(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("处理发布事件 - 远程服务失败时抛出异常重试")
    void testDoHandle_RemoteFailure() {
        when(postServiceClient.getPostById(anyLong()))
            .thenReturn(ApiResponse.fail(ResultCode.SERVICE_UNAVAILABLE, "文章服务暂时不可用"));

        assertThrows(IllegalStateException.class, () -> consumer.doHandle(event));
        verify(postSearchRepository, never()).index(org.mockito.ArgumentMatchers.any());
    }

    private PostDetailDTO buildPostDetail() {
        UserSimpleDTO author = new UserSimpleDTO();
        author.setId(10L);
        author.setNickname("author");

        PostDetailDTO post = new PostDetailDTO();
        post.setId(1L);
        post.setTitle("Test Post");
        post.setRaw("raw content");
        post.setExcerpt("excerpt");
        post.setAuthor(author);
        post.setCategories(List.of("Backend"));
        post.setTags(List.of(
            TagDTO.builder().id(1001L).name("Java").slug("java").build(),
            TagDTO.builder().id(1002L).name("Search").slug("search").build()
        ));
        post.setLikeCount(3);
        post.setCommentCount(4);
        post.setViewCount(5);
        return post;
    }
}
