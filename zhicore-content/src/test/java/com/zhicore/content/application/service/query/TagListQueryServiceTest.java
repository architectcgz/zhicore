package com.zhicore.content.application.service.query;

import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.assembler.TagAssembler;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagListQueryServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TagAssembler tagAssembler;

    @InjectMocks
    private TagListQueryService tagListQueryService;

    @Test
    void shouldReturnPagedTagResults() {
        Tag tag = Tag.create(1000L, "Java", "java");
        TagDTO tagDTO = TagDTO.builder()
                .id(1000L)
                .name("Java")
                .slug("java")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(tagRepository.findAll(any())).thenReturn(new PageImpl<>(List.of(tag), PageRequest.of(0, 20), 1));
        when(tagAssembler.toDTO(tag)).thenReturn(tagDTO);

        PageResult<TagDTO> result = tagListQueryService.listTags(0, 20);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("Java", result.getRecords().get(0).getName());
        verify(tagRepository).findAll(any());
        verify(tagAssembler).toDTO(tag);
    }

    @Test
    void shouldReturnEmptyPageWhenNoTagsFound() {
        when(tagRepository.findAll(any())).thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0));

        PageResult<TagDTO> result = tagListQueryService.listTags(0, 20);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(tagRepository).findAll(any());
        verify(tagAssembler, never()).toDTO(any());
    }
}
