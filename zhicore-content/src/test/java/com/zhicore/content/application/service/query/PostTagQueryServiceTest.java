package com.zhicore.content.application.service.query;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostTagQueryServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private PostTagRepository postTagRepository;
    @Mock private TagRepository tagRepository;

    @InjectMocks
    private PostTagQueryService postTagQueryService;

    @Test
    void shouldReturnEmptyListWhenPostHasNoTags() {
        Post post = Post.createDraft(PostId.of(1001L), UserId.of(2001L), "test");
        when(postRepository.findById(1001L)).thenReturn(Optional.of(post));
        when(postTagRepository.findTagIdsByPostId(1001L)).thenReturn(List.of());

        List<TagDTO> result = postTagQueryService.getPostTags(1001L);

        assertTrue(result.isEmpty());
        verify(tagRepository, never()).findByIdIn(anyList());
    }

    @Test
    void shouldReturnMappedTags() {
        Post post = Post.createDraft(PostId.of(1001L), UserId.of(2001L), "test");
        Tag java = Tag.create(1L, "Java", "java");
        Tag spring = Tag.create(2L, "Spring", "spring");
        when(postRepository.findById(1001L)).thenReturn(Optional.of(post));
        when(postTagRepository.findTagIdsByPostId(1001L)).thenReturn(List.of(1L, 2L));
        when(tagRepository.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(java, spring));

        List<TagDTO> result = postTagQueryService.getPostTags(1001L);

        assertEquals(2, result.size());
        assertEquals("Java", result.get(0).getName());
        assertEquals("spring", result.get(1).getSlug());
    }

    @Test
    void shouldRejectDeletedPost() {
        Post post = Post.createDraft(PostId.of(1001L), UserId.of(2001L), "test");
        post.delete();
        when(postRepository.findById(1001L)).thenReturn(Optional.of(post));

        assertThrows(BusinessException.class, () -> postTagQueryService.getPostTags(1001L));
    }
}
