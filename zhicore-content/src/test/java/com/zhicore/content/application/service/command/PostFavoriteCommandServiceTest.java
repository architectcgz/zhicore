package com.zhicore.content.application.service.command;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.repo.PostStatsRepository;
import com.zhicore.content.application.port.store.PostCacheInvalidationStore;
import com.zhicore.content.application.port.store.PostFavoriteStore;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.model.WriteState;
import com.zhicore.content.domain.repository.PostFavoriteRepository;
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

import java.time.OffsetDateTime;
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
@DisplayName("PostFavoriteCommandService 单元测试")
class PostFavoriteCommandServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long POST_ID = 2001L;
    private static final Long AUTHOR_ID = 3001L;
    private static final Long FAVORITE_ID = 4001L;

    @Mock private PostFavoriteRepository favoriteRepository;
    @Mock private PostRepository postRepository;
    @Mock private PostStatsRepository postStatsRepository;
    @Mock private IntegrationEventPublisher integrationEventPublisher;
    @Mock private PostFavoriteStore postFavoriteStore;
    @Mock private PostCacheInvalidationStore postCacheInvalidationStore;
    @Mock private IdGeneratorFeignClient idGeneratorFeignClient;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter counter;

    private PostFavoriteCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new PostFavoriteCommandService(
                favoriteRepository,
                postRepository,
                postStatsRepository,
                integrationEventPublisher,
                postFavoriteStore,
                postCacheInvalidationStore,
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
    void shouldFavoritePostSuccessfully() {
        when(postFavoriteStore.isFavorited(USER_ID, POST_ID)).thenReturn(null);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(createPublishedPost()));
        when(favoriteRepository.save(any())).thenReturn(true);
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(FAVORITE_ID));

        commandService.favoritePost(USER_ID, POST_ID);

        verify(favoriteRepository).save(any());
        verify(postStatsRepository).incrementFavoriteCount(PostId.of(POST_ID));
        verify(postFavoriteStore).incrementFavoriteCount(POST_ID);
        verify(postFavoriteStore).markFavorited(USER_ID, POST_ID);
        verify(integrationEventPublisher).publish(any());
    }

    @Test
    void shouldRejectWhenAlreadyFavoritedInCache() {
        when(postFavoriteStore.isFavorited(USER_ID, POST_ID)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> commandService.favoritePost(USER_ID, POST_ID));

        assertEquals("已经收藏过了", exception.getMessage());
        verify(postRepository, never()).findById(anyLong());
    }

    @Test
    void shouldRejectWhenPostNotPublished() {
        when(postFavoriteStore.isFavorited(USER_ID, POST_ID)).thenReturn(null);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(createDraftPost()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> commandService.favoritePost(USER_ID, POST_ID));

        assertEquals("文章未发布，无法收藏", exception.getMessage());
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void shouldSucceedWhenCacheUpdateFails() {
        when(postFavoriteStore.isFavorited(USER_ID, POST_ID)).thenReturn(null);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(createPublishedPost()));
        when(favoriteRepository.save(any())).thenReturn(true);
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(FAVORITE_ID));
        doThrow(new RuntimeException("Redis down")).when(postFavoriteStore).incrementFavoriteCount(POST_ID);

        assertDoesNotThrow(() -> commandService.favoritePost(USER_ID, POST_ID));
        verify(favoriteRepository).save(any());
        verify(postStatsRepository).incrementFavoriteCount(PostId.of(POST_ID));
        verify(integrationEventPublisher).publish(any());
    }

    @Test
    void shouldFailBeforeCacheUpdateWhenOutboxPublishFails() {
        when(postFavoriteStore.isFavorited(USER_ID, POST_ID)).thenReturn(null);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(createPublishedPost()));
        when(favoriteRepository.save(any())).thenReturn(true);
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(FAVORITE_ID));
        doThrow(new RuntimeException("outbox down")).when(integrationEventPublisher).publish(any());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> commandService.favoritePost(USER_ID, POST_ID));

        assertEquals("outbox down", exception.getMessage());
        verify(favoriteRepository).save(any());
        verify(postStatsRepository).incrementFavoriteCount(PostId.of(POST_ID));
        verify(postFavoriteStore, never()).incrementFavoriteCount(anyLong());
        verify(postFavoriteStore, never()).markFavorited(anyLong(), anyLong());
    }

    @Test
    void shouldRejectWhenDuplicateFavoriteDetectedByRepository() {
        when(postFavoriteStore.isFavorited(USER_ID, POST_ID)).thenReturn(null);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(createPublishedPost()));
        when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(ApiResponse.success(FAVORITE_ID));
        when(favoriteRepository.save(any())).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> commandService.favoritePost(USER_ID, POST_ID));

        assertEquals("已经收藏过了", exception.getMessage());
        verify(postStatsRepository, never()).incrementFavoriteCount(any());
        verify(integrationEventPublisher, never()).publish(any());
        verify(postFavoriteStore, never()).incrementFavoriteCount(anyLong());
        verify(postFavoriteStore, never()).markFavorited(anyLong(), anyLong());
    }

    private Post createPublishedPost() {
        OffsetDateTime now = OffsetDateTime.now();
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
                5L
        ));
    }

    private Post createDraftPost() {
        OffsetDateTime now = OffsetDateTime.now();
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
