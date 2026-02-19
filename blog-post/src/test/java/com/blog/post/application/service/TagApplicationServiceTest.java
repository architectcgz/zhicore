package com.blog.post.application.service;

import com.blog.common.exception.ResourceNotFoundException;
import com.blog.common.result.PageResult;
import com.blog.post.application.assembler.TagAssembler;
import com.blog.post.application.dto.TagDTO;
import com.blog.post.application.dto.TagStatsDTO;
import com.blog.post.domain.model.Tag;
import com.blog.post.domain.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TagApplicationService 单元测试
 * 
 * 测试标签应用服务的核心功能：
 * - getTag: 获取标签详情
 * - listTags: 获取标签列表（分页）
 * - searchTags: 搜索标签
 * 
 * Feature: post-tag-need
 * Validates: Requirements 4.1.4
 * 
 * @author Blog Team
 */
@ExtendWith(MockitoExtension.class)
class TagApplicationServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private TagAssembler tagAssembler;

    @InjectMocks
    private TagApplicationService tagApplicationService;

    private Tag testTag;
    private TagDTO testTagDTO;

    @BeforeEach
    void setUp() {
        // 创建测试数据
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

    // ==================== getTag 测试 ====================

    /**
     * 测试 getTag - 正常获取标签
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void getTagShouldReturnTagDTO() {
        // Given
        String slug = "java";
        when(tagRepository.findBySlug(slug)).thenReturn(Optional.of(testTag));
        when(tagAssembler.toDTO(testTag)).thenReturn(testTagDTO);

        // When
        TagDTO result = tagApplicationService.getTag(slug);

        // Then
        assertNotNull(result);
        assertEquals(testTagDTO.getId(), result.getId());
        assertEquals(testTagDTO.getName(), result.getName());
        assertEquals(testTagDTO.getSlug(), result.getSlug());

        verify(tagRepository, times(1)).findBySlug(slug);
        verify(tagAssembler, times(1)).toDTO(testTag);
    }

    /**
     * 测试 getTag - 标签不存在应抛出异常
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void getTagWithNonExistentSlugShouldThrowException() {
        // Given
        String slug = "nonexistent";
        when(tagRepository.findBySlug(slug)).thenReturn(Optional.empty());

        // When & Then
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            tagApplicationService.getTag(slug);
        });

        assertTrue(exception.getMessage().contains("标签不存在"));
        assertTrue(exception.getMessage().contains(slug));

        verify(tagRepository, times(1)).findBySlug(slug);
        verify(tagAssembler, never()).toDTO(any());
    }

    /**
     * 测试 getTag - 空 slug 应抛出异常
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void getTagWithEmptySlugShouldThrowException() {
        // Given
        String emptySlug = "";
        when(tagRepository.findBySlug(emptySlug)).thenReturn(Optional.empty());

        // When & Then
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            tagApplicationService.getTag(emptySlug);
        });

        assertTrue(exception.getMessage().contains("标签不存在"));

        verify(tagRepository, times(1)).findBySlug(emptySlug);
        verify(tagAssembler, never()).toDTO(any());
    }

    /**
     * 测试 getTag - null slug 应抛出异常
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void getTagWithNullSlugShouldThrowException() {
        // Given
        String nullSlug = null;
        when(tagRepository.findBySlug(nullSlug)).thenReturn(Optional.empty());

        // When & Then
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            tagApplicationService.getTag(nullSlug);
        });

        assertTrue(exception.getMessage().contains("标签不存在"));

        verify(tagRepository, times(1)).findBySlug(nullSlug);
        verify(tagAssembler, never()).toDTO(any());
    }

    // ==================== listTags 测试 ====================

    /**
     * 测试 listTags - 正常分页查询
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void listTagsShouldReturnPagedResults() {
        // Given
        int page = 0;
        int size = 20;
        
        List<Tag> tags = new ArrayList<>();
        List<TagDTO> tagDTOs = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            Tag tag = Tag.create(1000L + i, "Tag" + i, "tag" + i);
            tags.add(tag);
            
            TagDTO dto = TagDTO.builder()
                    .id(1000L + i)
                    .name("Tag" + i)
                    .slug("tag" + i)
                    .build();
            tagDTOs.add(dto);
            
            when(tagAssembler.toDTO(tag)).thenReturn(dto);
        }
        
        Page<Tag> tagPage = new PageImpl<>(tags, PageRequest.of(page, size), 5);
        when(tagRepository.findAll(any(Pageable.class))).thenReturn(tagPage);

        // When
        PageResult<TagDTO> result = tagApplicationService.listTags(page, size);

        // Then
        assertNotNull(result);
        assertEquals(page, result.getCurrent());
        assertEquals(size, result.getSize());
        assertEquals(5L, result.getTotal());
        assertEquals(5, result.getRecords().size());

        verify(tagRepository, times(1)).findAll(any(Pageable.class));
        verify(tagAssembler, times(5)).toDTO(any(Tag.class));
    }

    /**
     * 测试 listTags - 空结果
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void listTagsWithNoResultsShouldReturnEmptyPage() {
        // Given
        int page = 0;
        int size = 20;
        
        Page<Tag> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);
        when(tagRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        // When
        PageResult<TagDTO> result = tagApplicationService.listTags(page, size);

        // Then
        assertNotNull(result);
        assertEquals(page, result.getCurrent());
        assertEquals(size, result.getSize());
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());

        verify(tagRepository, times(1)).findAll(any(Pageable.class));
        verify(tagAssembler, never()).toDTO(any());
    }

    /**
     * 测试 listTags - 第二页
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void listTagsSecondPageShouldReturnCorrectPage() {
        // Given
        int page = 1;
        int size = 10;
        
        List<Tag> tags = new ArrayList<>();
        List<TagDTO> tagDTOs = new ArrayList<>();
        
        for (int i = 10; i < 15; i++) {
            Tag tag = Tag.create(1000L + i, "Tag" + i, "tag" + i);
            tags.add(tag);
            
            TagDTO dto = TagDTO.builder()
                    .id(1000L + i)
                    .name("Tag" + i)
                    .slug("tag" + i)
                    .build();
            tagDTOs.add(dto);
            
            when(tagAssembler.toDTO(tag)).thenReturn(dto);
        }
        
        Page<Tag> tagPage = new PageImpl<>(tags, PageRequest.of(page, size), 15);
        when(tagRepository.findAll(any(Pageable.class))).thenReturn(tagPage);

        // When
        PageResult<TagDTO> result = tagApplicationService.listTags(page, size);

        // Then
        assertNotNull(result);
        assertEquals(page, result.getCurrent());
        assertEquals(size, result.getSize());
        assertEquals(15L, result.getTotal());
        assertEquals(5, result.getRecords().size());

        verify(tagRepository, times(1)).findAll(any(Pageable.class));
        verify(tagAssembler, times(5)).toDTO(any(Tag.class));
    }

    /**
     * 测试 listTags - 小页面大小
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void listTagsWithSmallPageSizeShouldWork() {
        // Given
        int page = 0;
        int size = 5;
        
        List<Tag> tags = new ArrayList<>();
        List<TagDTO> tagDTOs = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            Tag tag = Tag.create(1000L + i, "Tag" + i, "tag" + i);
            tags.add(tag);
            
            TagDTO dto = TagDTO.builder()
                    .id(1000L + i)
                    .name("Tag" + i)
                    .slug("tag" + i)
                    .build();
            tagDTOs.add(dto);
            
            when(tagAssembler.toDTO(tag)).thenReturn(dto);
        }
        
        Page<Tag> tagPage = new PageImpl<>(tags, PageRequest.of(page, size), 100);
        when(tagRepository.findAll(any(Pageable.class))).thenReturn(tagPage);

        // When
        PageResult<TagDTO> result = tagApplicationService.listTags(page, size);

        // Then
        assertNotNull(result);
        assertEquals(page, result.getCurrent());
        assertEquals(size, result.getSize());
        assertEquals(100L, result.getTotal());
        assertEquals(5, result.getRecords().size());

        verify(tagRepository, times(1)).findAll(any(Pageable.class));
    }

    // ==================== searchTags 测试 ====================

    /**
     * 测试 searchTags - 正常搜索
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void searchTagsShouldReturnMatchingTags() {
        // Given
        String keyword = "java";
        int limit = 10;
        
        List<Tag> tags = new ArrayList<>();
        List<TagDTO> tagDTOs = new ArrayList<>();
        
        String[] tagNames = {"Java", "JavaScript", "JavaFX"};
        for (int i = 0; i < tagNames.length; i++) {
            Tag tag = Tag.create(1000L + i, tagNames[i], tagNames[i].toLowerCase());
            tags.add(tag);
            
            TagDTO dto = TagDTO.builder()
                    .id(1000L + i)
                    .name(tagNames[i])
                    .slug(tagNames[i].toLowerCase())
                    .build();
            tagDTOs.add(dto);
            
            when(tagAssembler.toDTO(tag)).thenReturn(dto);
        }
        
        when(tagRepository.searchByName(keyword, limit)).thenReturn(tags);

        // When
        List<TagDTO> result = tagApplicationService.searchTags(keyword, limit);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(dto -> dto.getName().equals("Java")));
        assertTrue(result.stream().anyMatch(dto -> dto.getName().equals("JavaScript")));
        assertTrue(result.stream().anyMatch(dto -> dto.getName().equals("JavaFX")));

        verify(tagRepository, times(1)).searchByName(keyword, limit);
        verify(tagAssembler, times(3)).toDTO(any(Tag.class));
    }

    /**
     * 测试 searchTags - 空关键词应返回空列表
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void searchTagsWithEmptyKeywordShouldReturnEmptyList() {
        // Given
        String emptyKeyword = "";
        int limit = 10;

        // When
        List<TagDTO> result = tagApplicationService.searchTags(emptyKeyword, limit);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(tagRepository, never()).searchByName(anyString(), anyInt());
        verify(tagAssembler, never()).toDTO(any());
    }

    /**
     * 测试 searchTags - 空白关键词应返回空列表
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void searchTagsWithBlankKeywordShouldReturnEmptyList() {
        // Given
        String blankKeyword = "   ";
        int limit = 10;

        // When
        List<TagDTO> result = tagApplicationService.searchTags(blankKeyword, limit);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(tagRepository, never()).searchByName(anyString(), anyInt());
        verify(tagAssembler, never()).toDTO(any());
    }

    /**
     * 测试 searchTags - null 关键词应返回空列表
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void searchTagsWithNullKeywordShouldReturnEmptyList() {
        // Given
        String nullKeyword = null;
        int limit = 10;

        // When
        List<TagDTO> result = tagApplicationService.searchTags(nullKeyword, limit);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(tagRepository, never()).searchByName(anyString(), anyInt());
        verify(tagAssembler, never()).toDTO(any());
    }

    /**
     * 测试 searchTags - 无匹配结果
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void searchTagsWithNoMatchesShouldReturnEmptyList() {
        // Given
        String keyword = "nonexistent";
        int limit = 10;
        
        when(tagRepository.searchByName(keyword, limit)).thenReturn(Collections.emptyList());

        // When
        List<TagDTO> result = tagApplicationService.searchTags(keyword, limit);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(tagRepository, times(1)).searchByName(keyword, limit);
        verify(tagAssembler, never()).toDTO(any());
    }

    /**
     * 测试 searchTags - 限制返回数量
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void searchTagsWithLimitShouldRespectLimit() {
        // Given
        String keyword = "tag";
        int limit = 3;
        
        List<Tag> tags = new ArrayList<>();
        List<TagDTO> tagDTOs = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            Tag tag = Tag.create(1000L + i, "Tag" + i, "tag" + i);
            tags.add(tag);
            
            TagDTO dto = TagDTO.builder()
                    .id(1000L + i)
                    .name("Tag" + i)
                    .slug("tag" + i)
                    .build();
            tagDTOs.add(dto);
            
            when(tagAssembler.toDTO(tag)).thenReturn(dto);
        }
        
        when(tagRepository.searchByName(keyword, limit)).thenReturn(tags);

        // When
        List<TagDTO> result = tagApplicationService.searchTags(keyword, limit);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());

        verify(tagRepository, times(1)).searchByName(keyword, limit);
        verify(tagAssembler, times(3)).toDTO(any(Tag.class));
    }

    /**
     * 测试 searchTags - 关键词前后有空格应自动 trim
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void searchTagsWithKeywordHavingSpacesShouldTrim() {
        // Given
        String keywordWithSpaces = "  java  ";
        String trimmedKeyword = "java";
        int limit = 10;
        
        List<Tag> tags = List.of(testTag);
        when(tagRepository.searchByName(trimmedKeyword, limit)).thenReturn(tags);
        when(tagAssembler.toDTO(testTag)).thenReturn(testTagDTO);

        // When
        List<TagDTO> result = tagApplicationService.searchTags(keywordWithSpaces, limit);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        verify(tagRepository, times(1)).searchByName(trimmedKeyword, limit);
        verify(tagAssembler, times(1)).toDTO(testTag);
    }

    /**
     * 测试 searchTags - 单个字符关键词
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void searchTagsWithSingleCharKeywordShouldWork() {
        // Given
        String keyword = "J";
        int limit = 10;
        
        List<Tag> tags = List.of(testTag);
        when(tagRepository.searchByName(keyword, limit)).thenReturn(tags);
        when(tagAssembler.toDTO(testTag)).thenReturn(testTagDTO);

        // When
        List<TagDTO> result = tagApplicationService.searchTags(keyword, limit);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());

        verify(tagRepository, times(1)).searchByName(keyword, limit);
        verify(tagAssembler, times(1)).toDTO(testTag);
    }

    /**
     * 测试 searchTags - 中文关键词
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void searchTagsWithChineseKeywordShouldWork() {
        // Given
        String keyword = "数据库";
        int limit = 10;
        
        Tag chineseTag = Tag.create(2000L, "数据库", "shu-ju-ku");
        TagDTO chineseDTO = TagDTO.builder()
                .id(2000L)
                .name("数据库")
                .slug("shu-ju-ku")
                .build();
        
        when(tagRepository.searchByName(keyword, limit)).thenReturn(List.of(chineseTag));
        when(tagAssembler.toDTO(chineseTag)).thenReturn(chineseDTO);

        // When
        List<TagDTO> result = tagApplicationService.searchTags(keyword, limit);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("数据库", result.get(0).getName());

        verify(tagRepository, times(1)).searchByName(keyword, limit);
        verify(tagAssembler, times(1)).toDTO(chineseTag);
    }

    /**
     * 测试 searchTags - 特殊字符关键词
     * 
     * Validates: Requirements 4.1.4
     */
    @Test
    void searchTagsWithSpecialCharsKeywordShouldWork() {
        // Given
        String keyword = "C++";
        int limit = 10;
        
        Tag specialTag = Tag.create(3000L, "C++", "c");
        TagDTO specialDTO = TagDTO.builder()
                .id(3000L)
                .name("C++")
                .slug("c")
                .build();
        
        when(tagRepository.searchByName(keyword, limit)).thenReturn(List.of(specialTag));
        when(tagAssembler.toDTO(specialTag)).thenReturn(specialDTO);

        // When
        List<TagDTO> result = tagApplicationService.searchTags(keyword, limit);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("C++", result.get(0).getName());

        verify(tagRepository, times(1)).searchByName(keyword, limit);
        verify(tagAssembler, times(1)).toDTO(specialTag);
    }

    // ==================== getHotTags 测试 ====================

    /**
     * 测试 getHotTags - 正常获取热门标签
     * 
     * Validates: Requirements 4.4
     */
    @Test
    void getHotTagsShouldReturnTagsOrderedByPostCount() {
        // Given
        int limit = 10;
        
        // 模拟 tag_stats 查询结果（按 post_count 降序）
        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(Map.of("tag_id", 1001L, "post_count", 100));
        stats.add(Map.of("tag_id", 1002L, "post_count", 80));
        stats.add(Map.of("tag_id", 1003L, "post_count", 50));
        
        when(tagRepository.findHotTags(limit)).thenReturn(stats);
        
        // 模拟批量查询 Tag
        Tag tag1 = Tag.create(1001L, "Java", "java");
        Tag tag2 = Tag.create(1002L, "Python", "python");
        Tag tag3 = Tag.create(1003L, "JavaScript", "javascript");
        
        when(tagRepository.findByIdIn(List.of(1001L, 1002L, 1003L)))
                .thenReturn(List.of(tag1, tag2, tag3));

        // When
        List<TagStatsDTO> result = tagApplicationService.getHotTags(limit);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        
        // 验证排序（按 post_count 降序）
        assertEquals(1001L, result.get(0).getId());
        assertEquals("Java", result.get(0).getName());
        assertEquals("java", result.get(0).getSlug());
        assertEquals(100, result.get(0).getPostCount());
        
        assertEquals(1002L, result.get(1).getId());
        assertEquals("Python", result.get(1).getName());
        assertEquals(80, result.get(1).getPostCount());
        
        assertEquals(1003L, result.get(2).getId());
        assertEquals("JavaScript", result.get(2).getName());
        assertEquals(50, result.get(2).getPostCount());

        verify(tagRepository, times(1)).findHotTags(limit);
        verify(tagRepository, times(1)).findByIdIn(anyList());
    }

    /**
     * 测试 getHotTags - 无统计数据应返回空列表
     * 
     * Validates: Requirements 4.4
     */
    @Test
    void getHotTagsWithNoStatsShouldReturnEmptyList() {
        // Given
        int limit = 10;
        
        when(tagRepository.findHotTags(limit)).thenReturn(Collections.emptyList());

        // When
        List<TagStatsDTO> result = tagApplicationService.getHotTags(limit);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(tagRepository, times(1)).findHotTags(limit);
        verify(tagRepository, never()).findByIdIn(anyList());
    }

    /**
     * 测试 getHotTags - 限制返回数量
     * 
     * Validates: Requirements 4.4
     */
    @Test
    void getHotTagsWithLimitShouldRespectLimit() {
        // Given
        int limit = 5;
        
        // 模拟返回 5 个标签
        List<Map<String, Object>> stats = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            stats.add(Map.of("tag_id", 1000L + i, "post_count", 100 - i * 10));
        }
        
        when(tagRepository.findHotTags(limit)).thenReturn(stats);
        
        // 模拟批量查询 Tag
        List<Tag> tags = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tags.add(Tag.create(1000L + i, "Tag" + i, "tag" + i));
        }
        
        when(tagRepository.findByIdIn(anyList())).thenReturn(tags);

        // When
        List<TagStatsDTO> result = tagApplicationService.getHotTags(limit);

        // Then
        assertNotNull(result);
        assertEquals(5, result.size());

        verify(tagRepository, times(1)).findHotTags(limit);
        verify(tagRepository, times(1)).findByIdIn(anyList());
    }

    /**
     * 测试 getHotTags - 单个热门标签
     * 
     * Validates: Requirements 4.4
     */
    @Test
    void getHotTagsWithSingleTagShouldWork() {
        // Given
        int limit = 10;
        
        List<Map<String, Object>> stats = List.of(
                Map.of("tag_id", 1001L, "post_count", 100)
        );
        
        when(tagRepository.findHotTags(limit)).thenReturn(stats);
        
        Tag tag = Tag.create(1001L, "Java", "java");
        when(tagRepository.findByIdIn(List.of(1001L))).thenReturn(List.of(tag));

        // When
        List<TagStatsDTO> result = tagApplicationService.getHotTags(limit);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1001L, result.get(0).getId());
        assertEquals("Java", result.get(0).getName());
        assertEquals(100, result.get(0).getPostCount());

        verify(tagRepository, times(1)).findHotTags(limit);
        verify(tagRepository, times(1)).findByIdIn(List.of(1001L));
    }

    /**
     * 测试 getHotTags - 部分标签不存在（数据不一致场景）
     * 
     * Validates: Requirements 4.4
     */
    @Test
    void getHotTagsWithMissingTagsShouldSkipMissing() {
        // Given
        int limit = 10;
        
        // tag_stats 中有 3 个标签
        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(Map.of("tag_id", 1001L, "post_count", 100));
        stats.add(Map.of("tag_id", 1002L, "post_count", 80));
        stats.add(Map.of("tag_id", 1003L, "post_count", 50));
        
        when(tagRepository.findHotTags(limit)).thenReturn(stats);
        
        // 但只返回 2 个标签（1002 不存在）
        Tag tag1 = Tag.create(1001L, "Java", "java");
        Tag tag3 = Tag.create(1003L, "JavaScript", "javascript");
        
        when(tagRepository.findByIdIn(List.of(1001L, 1002L, 1003L)))
                .thenReturn(List.of(tag1, tag3));

        // When
        List<TagStatsDTO> result = tagApplicationService.getHotTags(limit);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        
        // 验证只包含存在的标签
        assertEquals(1001L, result.get(0).getId());
        assertEquals(1003L, result.get(1).getId());
        
        // 验证不包含不存在的标签
        assertFalse(result.stream().anyMatch(dto -> dto.getId().equals(1002L)));

        verify(tagRepository, times(1)).findHotTags(limit);
        verify(tagRepository, times(1)).findByIdIn(anyList());
    }

    /**
     * 测试 getHotTags - 零文章数量的标签
     * 
     * Validates: Requirements 4.4
     */
    @Test
    void getHotTagsWithZeroPostCountShouldWork() {
        // Given
        int limit = 10;
        
        List<Map<String, Object>> stats = List.of(
                Map.of("tag_id", 1001L, "post_count", 0)
        );
        
        when(tagRepository.findHotTags(limit)).thenReturn(stats);
        
        Tag tag = Tag.create(1001L, "NewTag", "newtag");
        when(tagRepository.findByIdIn(List.of(1001L))).thenReturn(List.of(tag));

        // When
        List<TagStatsDTO> result = tagApplicationService.getHotTags(limit);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getPostCount());

        verify(tagRepository, times(1)).findHotTags(limit);
    }

    /**
     * 测试 getHotTags - 大数量限制
     * 
     * Validates: Requirements 4.4
     */
    @Test
    void getHotTagsWithLargeLimitShouldWork() {
        // Given
        int limit = 100;
        
        // 模拟返回 10 个标签（实际数量少于限制）
        List<Map<String, Object>> stats = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            stats.add(Map.of("tag_id", 1000L + i, "post_count", 100 - i * 5));
        }
        
        when(tagRepository.findHotTags(limit)).thenReturn(stats);
        
        List<Tag> tags = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tags.add(Tag.create(1000L + i, "Tag" + i, "tag" + i));
        }
        
        when(tagRepository.findByIdIn(anyList())).thenReturn(tags);

        // When
        List<TagStatsDTO> result = tagApplicationService.getHotTags(limit);

        // Then
        assertNotNull(result);
        assertEquals(10, result.size());

        verify(tagRepository, times(1)).findHotTags(limit);
    }

    /**
     * 测试 getHotTags - 验证排序保持一致
     * 
     * Validates: Requirements 4.4
     */
    @Test
    void getHotTagsShouldMaintainOrderFromStats() {
        // Given
        int limit = 10;
        
        // 特定顺序的统计数据
        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(Map.of("tag_id", 1003L, "post_count", 150));
        stats.add(Map.of("tag_id", 1001L, "post_count", 120));
        stats.add(Map.of("tag_id", 1002L, "post_count", 90));
        
        when(tagRepository.findHotTags(limit)).thenReturn(stats);
        
        // 批量查询返回的顺序可能不同
        Tag tag1 = Tag.create(1001L, "Java", "java");
        Tag tag2 = Tag.create(1002L, "Python", "python");
        Tag tag3 = Tag.create(1003L, "JavaScript", "javascript");
        
        when(tagRepository.findByIdIn(List.of(1003L, 1001L, 1002L)))
                .thenReturn(List.of(tag1, tag2, tag3)); // 不同顺序

        // When
        List<TagStatsDTO> result = tagApplicationService.getHotTags(limit);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        
        // 验证结果保持 stats 的顺序
        assertEquals(1003L, result.get(0).getId());
        assertEquals(150, result.get(0).getPostCount());
        
        assertEquals(1001L, result.get(1).getId());
        assertEquals(120, result.get(1).getPostCount());
        
        assertEquals(1002L, result.get(2).getId());
        assertEquals(90, result.get(2).getPostCount());

        verify(tagRepository, times(1)).findHotTags(limit);
    }
}
