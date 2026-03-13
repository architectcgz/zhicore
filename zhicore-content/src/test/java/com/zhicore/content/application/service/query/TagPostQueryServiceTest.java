package com.zhicore.content.application.service.query;

import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.model.WriteState;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagPostQueryServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private PostTagRepository postTagRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private TagPostQueryService tagPostQueryService;

    @Test
    void shouldThrowWhenTagMissing() {
        when(tagRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tagPostQueryService.getPostsByTag("missing", 0, 20));
        verify(tagRepository).findBySlug("missing");
    }

    @Test
    void shouldReturnEmptyPageWhenTagHasNoPosts() {
        Tag tag = Tag.create(1000L, "Java", "java");
        PageRequest pageable = PageRequest.of(0, 20);
        when(tagRepository.findBySlug("java")).thenReturn(Optional.of(tag));
        when(postTagRepository.findPostIdsByTagId(1000L, pageable))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        PageResult<PostVO> result = tagPostQueryService.getPostsByTag("java", 0, 20);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    void shouldKeepPostOrderFromPageIds() {
        Tag tag = Tag.create(1000L, "Java", "java");
        PageRequest pageable = PageRequest.of(0, 20);
        Post first = post(2001L, "Post A");
        Post second = post(2002L, "Post B");
        when(tagRepository.findBySlug("java")).thenReturn(Optional.of(tag));
        when(postTagRepository.findPostIdsByTagId(1000L, pageable))
                .thenReturn(new PageImpl<>(List.of(2002L, 2001L), pageable, 2));
        when(postRepository.findByIds(List.of(2002L, 2001L))).thenReturn(Map.of(
                2001L, first,
                2002L, second
        ));

        PageResult<PostVO> result = tagPostQueryService.getPostsByTag("java", 0, 20);

        assertEquals(2, result.getRecords().size());
        assertEquals("Post B", result.getRecords().get(0).getTitle());
        assertEquals("Post A", result.getRecords().get(1).getTitle());
        verify(postRepository).findByIds(List.of(2002L, 2001L));
    }

    private Post post(Long postId, String title) {
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
                PostStatus.PUBLISHED,
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
