package com.zhicore.content.application.service.query;

import com.zhicore.content.application.port.store.PostLikeStore;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.repository.PostLikeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostLikeQueryService 单元测试")
class PostLikeQueryServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long POST_ID = 2001L;

    @Mock private PostLikeRepository likeRepository;
    @Mock private PostLikeStore postLikeStore;

    @InjectMocks
    private PostLikeQueryService queryService;

    @Test
    void shouldReturnTrueWhenLikedCacheHit() {
        when(postLikeStore.isLiked(USER_ID, POST_ID)).thenReturn(true);

        assertTrue(queryService.isLiked(USER_ID, POST_ID));
        verify(likeRepository, never()).exists(any(PostId.class), any(UserId.class));
    }

    @Test
    void shouldMergeCacheAndDatabaseForBatchCheck() {
        List<Long> postIds = List.of(POST_ID, POST_ID + 1, POST_ID + 2);
        when(postLikeStore.findLikedPostIds(USER_ID, postIds)).thenReturn(Set.of(POST_ID));
        when(likeRepository.findLikedPostIds(UserId.of(USER_ID), List.of(PostId.of(POST_ID + 1), PostId.of(POST_ID + 2))))
                .thenReturn(List.of(PostId.of(POST_ID + 2)));

        var result = queryService.batchCheckLiked(USER_ID, postIds);

        assertEquals(Boolean.TRUE, result.get(POST_ID));
        assertEquals(Boolean.FALSE, result.get(POST_ID + 1));
        assertEquals(Boolean.TRUE, result.get(POST_ID + 2));
        verify(postLikeStore).markLiked(USER_ID, POST_ID + 2);
    }

    @Test
    void shouldBackfillLikeCountWhenCacheMiss() {
        when(postLikeStore.getLikeCount(POST_ID)).thenReturn(null);
        when(likeRepository.countByPostId(PostId.of(POST_ID))).thenReturn(12);

        assertEquals(12, queryService.getLikeCount(POST_ID));
        verify(postLikeStore).cacheLikeCount(POST_ID, 12);
    }
}
