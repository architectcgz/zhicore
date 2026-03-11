package com.zhicore.content.application.service;

import com.zhicore.api.dto.post.PostDTO;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostBatchQueryServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostBatchQueryService postBatchQueryService;

    @Test
    void shouldReturnEmptyMapWhenIdsMissing() {
        assertTrue(postBatchQueryService.batchGetPosts(null).isEmpty());
        assertTrue(postBatchQueryService.batchGetPosts(Set.of()).isEmpty());
        verifyNoInteractions(postRepository);
    }

    @Test
    void shouldIgnoreDeletedPostsWhenBatchQuerying() {
        Post active = post(1001L, "Active", PostStatus.PUBLISHED);
        Post deleted = post(1002L, "Deleted", PostStatus.DELETED);
        when(postRepository.findByIds(org.mockito.ArgumentMatchers.anyList())).thenReturn(Map.of(
                1001L, active,
                1002L, deleted
        ));

        Map<Long, PostDTO> result = postBatchQueryService.batchGetPosts(Set.of(1001L, 1002L));

        assertEquals(1, result.size());
        assertEquals("Active", result.get(1001L).getTitle());
        assertTrue(!result.containsKey(1002L));
    }

    private Post post(Long postId, String title, PostStatus status) {
        PostId id = PostId.of(postId);
        UserId ownerId = UserId.of(2001L);
        LocalDateTime now = LocalDateTime.now();
        return Post.reconstitute(new Post.Snapshot(
                id,
                ownerId,
                OwnerSnapshot.createDefault(ownerId),
                title,
                title + " excerpt",
                null,
                status,
                null,
                null,
                now,
                null,
                now.minusHours(1),
                now,
                false,
                PostStats.empty(id),
                WriteState.NONE,
                null,
                1L
        ));
    }
}
