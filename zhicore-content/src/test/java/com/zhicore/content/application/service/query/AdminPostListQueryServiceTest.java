package com.zhicore.content.application.service.query;

import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.query.model.AdminPostQueryCriteria;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.model.WriteState;
import com.zhicore.content.application.port.repo.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPostListQueryServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private AdminPostListQueryService adminPostListQueryService;

    @Test
    void shouldQueryAdminPostsWithNormalizedCriteria() {
        Post post = Post.reconstitute(new Post.Snapshot(
                PostId.of(1001L),
                UserId.of(2001L),
                new OwnerSnapshot(UserId.of(2001L), "作者A", "avatar-1", 3L),
                "Test Title",
                "excerpt",
                "cover-1",
                PostStatus.PUBLISHED,
                null,
                Set.of(),
                OffsetDateTime.now().minusHours(1),
                null,
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now(),
                false,
                PostStats.empty(PostId.of(1001L)).incrementViews().incrementLikes(),
                WriteState.NONE,
                null,
                2L
        ));

        AdminPostQueryCriteria criteria = AdminPostQueryCriteria.of("  test  ", "published", 2001L, 0, 200);
        when(postRepository.findByConditions("test", String.valueOf(PostStatus.PUBLISHED.getCode()), 2001L, 0, 100))
                .thenReturn(java.util.List.of(post));
        when(postRepository.countByConditions("test", String.valueOf(PostStatus.PUBLISHED.getCode()), 2001L))
                .thenReturn(1L);

        PageResult<com.zhicore.api.dto.admin.PostManageDTO> result = adminPostListQueryService.query(criteria);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("作者A", result.getRecords().get(0).getAuthorName());
        assertEquals("PUBLISHED", result.getRecords().get(0).getStatus());
        verify(postRepository).findByConditions("test", String.valueOf(PostStatus.PUBLISHED.getCode()), 2001L, 0, 100);
        verify(postRepository).countByConditions("test", String.valueOf(PostStatus.PUBLISHED.getCode()), 2001L);
    }

    @Test
    void shouldIgnoreUnknownStatusFilter() {
        AdminPostQueryCriteria criteria = AdminPostQueryCriteria.of(null, "unknown", null, 1, 20);
        when(postRepository.findByConditions(null, null, null, 0, 20)).thenReturn(java.util.List.of());
        when(postRepository.countByConditions(null, null, null)).thenReturn(0L);

        PageResult<com.zhicore.api.dto.admin.PostManageDTO> result = adminPostListQueryService.query(criteria);

        assertEquals(0L, result.getTotal());
        verify(postRepository).findByConditions(null, null, null, 0, 20);
        verify(postRepository).countByConditions(null, null, null);
    }
}
