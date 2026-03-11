package com.zhicore.content.application.service;

import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.port.repo.PostRepository;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPostQueryServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostFileUrlResolver postFileUrlResolver;

    @InjectMocks
    private MyPostQueryService myPostQueryService;

    @Test
    void shouldQueryOwnerPostsAndResolveCoverImage() {
        Post post = post(1001L, "Draft A", "cover-1");
        when(postRepository.findByOwnerId(2001L, PostStatus.DRAFT, 20, 20)).thenReturn(List.of(post));
        when(postFileUrlResolver.resolve("cover-1")).thenReturn("https://cdn.test/cover-1");

        List<PostBriefVO> result = myPostQueryService.getMyPosts(2001L, "DRAFT", 2, 20);

        assertEquals(1, result.size());
        assertEquals("Draft A", result.get(0).getTitle());
        assertEquals("https://cdn.test/cover-1", result.get(0).getCoverImageUrl());
        verify(postRepository).findByOwnerId(2001L, PostStatus.DRAFT, 20, 20);
        verify(postFileUrlResolver).resolve("cover-1");
    }

    @Test
    void shouldSkipFileResolutionWhenCoverImageMissing() {
        Post post = post(1002L, "Draft B", null);
        when(postRepository.findByOwnerId(2001L, PostStatus.PUBLISHED, 0, 10)).thenReturn(List.of(post));

        List<PostBriefVO> result = myPostQueryService.getMyPosts(2001L, "PUBLISHED", 1, 10);

        assertEquals(1, result.size());
        assertNull(result.get(0).getCoverImageUrl());
        verify(postRepository).findByOwnerId(2001L, PostStatus.PUBLISHED, 0, 10);
    }

    private Post post(Long postId, String title, String coverImageId) {
        PostId id = PostId.of(postId);
        UserId ownerId = UserId.of(2001L);
        LocalDateTime publishedAt = LocalDateTime.now();
        return Post.reconstitute(new Post.Snapshot(
                id,
                ownerId,
                OwnerSnapshot.createDefault(ownerId),
                title,
                title + " excerpt",
                coverImageId,
                PostStatus.DRAFT,
                null,
                null,
                publishedAt,
                null,
                publishedAt.minusHours(1),
                publishedAt,
                false,
                PostStats.empty(id),
                WriteState.NONE,
                null,
                1L
        ));
    }
}
