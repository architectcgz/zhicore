package com.zhicore.content.application.service.command;

import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostTagCommandServiceTest {

    @Mock
    private PostTagRepository postTagRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TagCommandService tagCommandService;

    @InjectMocks
    private PostTagCommandService postTagCommandService;

    @Test
    void shouldReplaceTagsUsingCommandService() {
        when(postTagRepository.findTagIdsByPostId(PostId.of(2001L))).thenReturn(List.of(TagId.of(1L), TagId.of(2L)));
        when(tagCommandService.findOrCreateBatch(List.of("Java", "Spring"))).thenReturn(List.of(
                Tag.create(10L, "Java", "java"),
                Tag.create(11L, "Spring", "spring")
        ));

        PostTagCommandService.ReplaceResult result =
                postTagCommandService.replaceTags(2001L, List.of("Java", "Spring"));

        assertEquals(List.of(1L, 2L), result.oldTagIds());
        assertEquals(List.of(10L, 11L), result.newTagIds());
        verify(postTagRepository).detachAllByPostId(PostId.of(2001L));
        verify(postTagRepository).attachBatch(PostId.of(2001L), List.of(TagId.of(10L), TagId.of(11L)));
    }

    @Test
    void shouldSkipAttachWhenTagNamesEmpty() {
        when(postTagRepository.findTagIdsByPostId(PostId.of(2001L))).thenReturn(List.of(TagId.of(1L)));

        PostTagCommandService.ReplaceResult result =
                postTagCommandService.replaceTags(2001L, List.of());

        assertEquals(List.of(1L), result.oldTagIds());
        assertEquals(List.of(), result.newTagIds());
        verify(postTagRepository).detachAllByPostId(PostId.of(2001L));
        verify(postTagRepository, never()).attachBatch(PostId.of(2001L), List.<TagId>of());
        verify(tagCommandService, never()).findOrCreateBatch(List.of());
    }

    @Test
    void shouldReturnRemainingTagNamesAfterDetach() {
        when(postTagRepository.findTagIdsByPostId(PostId.of(2001L))).thenReturn(List.of(TagId.of(1L), TagId.of(2L), TagId.of(3L)));
        when(tagRepository.findByIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(
                Tag.create(1L, "Java", "java"),
                Tag.create(2L, "Spring", "spring"),
                Tag.create(3L, "Go", "go")
        ));

        List<String> remaining = postTagCommandService.listRemainingTagNames(2001L, "spring");

        assertEquals(List.of("Java", "Go"), remaining);
    }
}
