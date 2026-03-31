package com.zhicore.content.application.service.query;

import com.zhicore.common.result.HybridPageRequest;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.query.model.CursorToken;
import com.zhicore.content.application.query.model.PostListQuery;
import com.zhicore.content.application.query.model.PostListSort;
import com.zhicore.content.application.service.PostFileUrlResolver;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.model.WriteState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishedPostQueryServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private PostFileUrlResolver postFileUrlResolver;

    @InjectMocks
    private PublishedPostQueryService publishedPostQueryService;

    @Test
    void shouldQueryPopularListForPopularSort() {
        Post post = published("Popular Post", 1001L, OffsetDateTime.now());
        when(postRepository.findPublishedPopular(0, 20)).thenReturn(List.of(post));
        when(postRepository.countPublished()).thenReturn(1L);

        HybridPageResult<PostBriefVO> result = publishedPostQueryService.getPostList(PostListQuery.builder()
                .page(1)
                .size(20)
                .sort(PostListSort.POPULAR)
                .build());

        assertEquals(1, result.getItems().size());
        assertEquals("Popular Post", result.getItems().get(0).getTitle());
        verify(postRepository).findPublishedPopular(0, 20);
        verify(postRepository, never()).findPublishedCursor(any(), any(), anyInt());
    }

    @Test
    void shouldReturnCursorPageForLatestSort() {
        OffsetDateTime now = OffsetDateTime.now();
        Post first = published("Post A", 1001L, now);
        Post second = published("Post B", 1002L, now.minusMinutes(1));
        when(postRepository.findPublishedCursor(now, 1001L, 3)).thenReturn(List.of(first, second));

        String cursor = CursorToken.encode(new CursorToken(now, 1001L));
        HybridPageResult<PostBriefVO> result = publishedPostQueryService.getPostList(PostListQuery.builder()
                .cursor(cursor)
                .size(2)
                .sort(PostListSort.LATEST)
                .build());

        assertEquals(2, result.getItems().size());
        assertFalse(result.isHasMore());
        verify(postRepository).findPublishedCursor(now, 1001L, 3);
    }

    @Test
    void shouldDelegateHybridCursorMode() {
        OffsetDateTime now = OffsetDateTime.now();
        Post first = published("Cursor Post", 1001L, now);
        when(postRepository.findPublishedCursor(now, 1001L, 3)).thenReturn(List.of(first));

        HybridPageRequest request = HybridPageRequest.ofCursor(CursorToken.encode(new CursorToken(now, 1001L)), 2);
        HybridPageResult<PostBriefVO> result = publishedPostQueryService.getPublishedPostsHybrid(request);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        verify(postRepository).findPublishedCursor(now, 1001L, 3);
    }

    private Post published(String title, Long postId, OffsetDateTime publishedAt) {
        PostId id = PostId.of(postId);
        UserId ownerId = UserId.of(2001L);
        OffsetDateTime createdAt = publishedAt.minusHours(1);
        return Post.reconstitute(new Post.Snapshot(
                id,
                ownerId,
                OwnerSnapshot.createDefault(ownerId),
                title,
                title + " excerpt",
                null,
                PostStatus.PUBLISHED,
                null,
                null,
                publishedAt,
                null,
                createdAt,
                publishedAt,
                false,
                PostStats.empty(id),
                WriteState.NONE,
                null,
                1L
        ));
    }
}
