package com.zhicore.content.application.service.command;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.PostLikeStore;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.model.WriteState;
import com.zhicore.content.domain.repository.PostLikeRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PostLikeCommandService 单元测试")
class PostLikeCommandServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long POST_ID = 2001L;
    private static final Long AUTHOR_ID = 3001L;
    private static final Long LIKE_ID = 4001L;

    @Mock private PostLikeRepository likeRepository;
    @Mock private PostRepository postRepository;
    @Mock private IntegrationEventPublisher integrationEventPublisher;
    @Mock private PostLikeStore postLikeStore;
    @Mock private IdGeneratorFeignClient idGeneratorFeignClient;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter counter;

    private PostLikeCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new PostLikeCommandService(
                likeRepository,
                postRepository,
                integrationEventPublisher,
                postLikeStore,
                idGeneratorFeignClient,
                transactionTemplate,
                meterRegistry
        );

        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action =
                    invocation.getArgument(0, java.util.function.Consumer.class);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void shouldLikePostSuccessfully() {
        when(postLikeStore.isLiked(USER_ID, POST_ID)).thenReturn(null);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(createPublishedPost()));
        when(likeRepository.exists(POST_ID, USER_ID)).thenReturn(false);
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(LIKE_ID));

        commandService.likePost(USER_ID, POST_ID);

        verify(likeRepository).save(any());
        verify(postLikeStore).incrementLikeCount(POST_ID);
        verify(postLikeStore).markLiked(USER_ID, POST_ID);
        verify(integrationEventPublisher).publish(any());
    }

    @Test
    void shouldRejectWhenAlreadyLikedInCache() {
        when(postLikeStore.isLiked(USER_ID, POST_ID)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> commandService.likePost(USER_ID, POST_ID));

        assertEquals("已经点赞过了", exception.getMessage());
        verify(postRepository, never()).findById(anyLong());
    }

    @Test
    void shouldRejectWhenPostNotPublished() {
        when(postLikeStore.isLiked(USER_ID, POST_ID)).thenReturn(null);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(createDraftPost()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> commandService.likePost(USER_ID, POST_ID));

        assertEquals("文章未发布，无法点赞", exception.getMessage());
        verify(likeRepository, never()).save(any());
    }

    @Test
    void shouldSucceedWhenCacheUpdateFails() {
        when(postLikeStore.isLiked(USER_ID, POST_ID)).thenReturn(null);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(createPublishedPost()));
        when(likeRepository.exists(POST_ID, USER_ID)).thenReturn(false);
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(LIKE_ID));
        doThrow(new RuntimeException("Redis down")).when(postLikeStore).incrementLikeCount(POST_ID);

        assertDoesNotThrow(() -> commandService.likePost(USER_ID, POST_ID));
        verify(likeRepository).save(any());
        verify(integrationEventPublisher).publish(any());
    }

    private Post createPublishedPost() {
        LocalDateTime now = LocalDateTime.now();
        return Post.reconstitute(new Post.Snapshot(
                PostId.of(POST_ID),
                UserId.of(AUTHOR_ID),
                null,
                "title",
                "excerpt",
                null,
                PostStatus.PUBLISHED,
                null,
                Set.of(),
                now.minusHours(1),
                null,
                now.minusDays(1),
                now,
                false,
                PostStats.empty(PostId.of(POST_ID)),
                WriteState.PUBLISHED,
                null,
                3L
        ));
    }

    private Post createDraftPost() {
        LocalDateTime now = LocalDateTime.now();
        return Post.reconstitute(new Post.Snapshot(
                PostId.of(POST_ID),
                UserId.of(AUTHOR_ID),
                null,
                "title",
                "excerpt",
                null,
                PostStatus.DRAFT,
                null,
                Set.of(),
                null,
                null,
                now.minusDays(1),
                now,
                false,
                PostStats.empty(PostId.of(POST_ID)),
                WriteState.DRAFT_ONLY,
                null,
                1L
        ));
    }
}
