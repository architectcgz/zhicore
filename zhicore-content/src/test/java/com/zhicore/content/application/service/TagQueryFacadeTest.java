package com.zhicore.content.application.service;

import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.dto.TagStatsDTO;
import com.zhicore.content.application.query.TagQuery;
import com.zhicore.content.application.query.view.HotTagView;
import com.zhicore.content.application.query.view.TagDetailView;
import com.zhicore.content.application.query.view.TagListItemView;
import com.zhicore.content.domain.model.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagQueryFacadeTest {

    @Mock private TagQuery tagQuery;
    @Mock private TagListQueryService tagListQueryService;
    @Mock private TagPostQueryService tagPostQueryService;

    @InjectMocks
    private TagQueryFacade tagQueryFacade;

    private Tag testTag;
    private TagDTO testTagDTO;

    @BeforeEach
    void setUp() {
        testTag = Tag.create(1000L, "Java", "java");
        testTagDTO = TagDTO.builder()
                .id(1000L)
                .name("Java")
                .slug("java")
                .description(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getTagShouldReturnTagDTO() {
        when(tagQuery.getDetailBySlug("java")).thenReturn(TagDetailView.builder()
                .id(testTagDTO.getId())
                .name(testTagDTO.getName())
                .slug(testTagDTO.getSlug())
                .description(testTagDTO.getDescription())
                .createdAt(testTagDTO.getCreatedAt())
                .updatedAt(testTagDTO.getUpdatedAt())
                .build());

        TagDTO result = tagQueryFacade.getTag("java");

        assertEquals(testTagDTO.getId(), result.getId());
        assertEquals(testTagDTO.getName(), result.getName());
        verify(tagQuery, times(1)).getDetailBySlug("java");
    }

    @Test
    void getTagWithNonExistentSlugShouldThrowException() {
        when(tagQuery.getDetailBySlug("nonexistent")).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> tagQueryFacade.getTag("nonexistent")
        );

        assertTrue(exception.getMessage().contains("标签不存在"));
        verify(tagQuery, times(1)).getDetailBySlug("nonexistent");
    }

    @Test
    void listTagsShouldReturnPagedResults() {
        PageResult<TagDTO> pageResult = PageResult.of(0, 20, 1, List.of(testTagDTO));
        when(tagListQueryService.listTags(0, 20)).thenReturn(pageResult);

        PageResult<TagDTO> result = tagQueryFacade.listTags(0, 20);

        assertEquals(0, result.getCurrent());
        assertEquals(20, result.getSize());
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        verify(tagListQueryService, times(1)).listTags(0, 20);
    }

    @Test
    void listTagsWithNoResultsShouldReturnEmptyPage() {
        when(tagListQueryService.listTags(0, 20))
                .thenReturn(PageResult.of(0, 20, 0, Collections.emptyList()));

        PageResult<TagDTO> result = tagQueryFacade.listTags(0, 20);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(tagListQueryService, times(1)).listTags(0, 20);
    }

    @Test
    void searchTagsWithBlankKeywordShouldReturnEmptyList() {
        List<TagDTO> result = tagQueryFacade.searchTags("   ", 10);

        assertTrue(result.isEmpty());
        verify(tagQuery, never()).searchByName(anyString(), anyInt());
    }

    @Test
    void searchTagsShouldTrimKeywordAndMapQueryViews() {
        when(tagQuery.searchByName("java", 10)).thenReturn(List.of(
                TagListItemView.builder().id(1000L).name("Java").slug("java").build(),
                TagListItemView.builder().id(1001L).name("JavaScript").slug("javascript").build()
        ));

        List<TagDTO> result = tagQueryFacade.searchTags("  java  ", 10);

        assertEquals(2, result.size());
        assertEquals("Java", result.get(0).getName());
        verify(tagQuery, times(1)).searchByName("java", 10);
    }

    @Test
    void getHotTagsShouldMapQueryViews() {
        when(tagQuery.getHotTags(10)).thenReturn(List.of(
                HotTagView.builder().id(1001L).name("Java").slug("java").postCount(100L).build(),
                HotTagView.builder().id(1002L).name("Python").slug("python").postCount(80L).build()
        ));

        List<TagStatsDTO> result = tagQueryFacade.getHotTags(10);

        assertEquals(2, result.size());
        assertEquals(100, result.get(0).getPostCount());
        assertEquals("Python", result.get(1).getName());
        verify(tagQuery, times(1)).getHotTags(10);
    }

    @Test
    void getHotTagsWithNoDataShouldReturnEmptyList() {
        when(tagQuery.getHotTags(10)).thenReturn(Collections.emptyList());

        List<TagStatsDTO> result = tagQueryFacade.getHotTags(10);

        assertTrue(result.isEmpty());
        verify(tagQuery, times(1)).getHotTags(10);
    }
}
