package com.blog.post.domain.service;

import com.blog.post.domain.model.Tag;
import com.blog.post.domain.repository.TagRepository;
import com.blog.post.domain.service.impl.TagDomainServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TagDomainService 单元测试
 *
 * @author Blog Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TagDomainService 单元测试")
class TagDomainServiceTest {

    @Mock
    private TagRepository tagRepository;

    private TagDomainService tagDomainService;

    @BeforeEach
    void setUp() {
        tagDomainService = new TagDomainServiceImpl(tagRepository);
    }

    // ==================== Slug 规范化测试 ====================

    @Test
    @DisplayName("测试 Slug 规范化 - 英文大小写")
    void testNormalizeToSlug_EnglishCase() {
        // Given
        String name = "Spring Boot";

        // When
        String slug = tagDomainService.normalizeToSlug(name);

        // Then
        assertEquals("spring-boot", slug);
    }

    @Test
    @DisplayName("测试 Slug 规范化 - 去除空格")
    void testNormalizeToSlug_TrimSpaces() {
        // Given
        String name = "  Java  ";

        // When
        String slug = tagDomainService.normalizeToSlug(name);

        // Then
        assertEquals("java", slug);
    }

    @Test
    @DisplayName("测试 Slug 规范化 - 特殊字符过滤")
    void testNormalizeToSlug_SpecialChars() {
        // Given
        String name = "C++";

        // When
        String slug = tagDomainService.normalizeToSlug(name);

        // Then
        assertEquals("c", slug);
    }

    @Test
    @DisplayName("测试 Slug 规范化 - 中文转拼音")
    void testNormalizeToSlug_ChineseToPinyin() {
        // Given
        String name = "数据库";

        // When
        String slug = tagDomainService.normalizeToSlug(name);

        // Then
        assertEquals("shujuku", slug);
    }

    @Test
    @DisplayName("测试 Slug 规范化 - 小写转换")
    void testNormalizeToSlug_Lowercase() {
        // Given
        String name = "PostgreSQL";

        // When
        String slug = tagDomainService.normalizeToSlug(name);

        // Then
        assertEquals("postgresql", slug);
    }

    @Test
    @DisplayName("测试 Slug 规范化 - 空名称抛出异常")
    void testNormalizeToSlug_EmptyName() {
        // Given
        String name = "   ";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            tagDomainService.normalizeToSlug(name);
        });
    }

    @Test
    @DisplayName("测试 Slug 规范化 - 多个空格替换为单个连字符")
    void testNormalizeToSlug_MultipleSpaces() {
        // Given
        String name = "Spring   Boot   Framework";

        // When
        String slug = tagDomainService.normalizeToSlug(name);

        // Then
        assertEquals("spring-boot-framework", slug);
    }

    // ==================== Tag 验证测试 ====================

    @Test
    @DisplayName("测试 Tag 验证 - 空名称")
    void testValidateTagName_EmptyName() {
        // Given
        String name = "";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 超长名称")
    void testValidateTagName_TooLong() {
        // Given
        String name = "a".repeat(51);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 非法字符")
    void testValidateTagName_IllegalChars() {
        // Given
        String name = "Tag@#$%";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 合法名称")
    void testValidateTagName_ValidName() {
        // Given
        String name = "Spring Boot";

        // When & Then
        assertDoesNotThrow(() -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 中文名称")
    void testValidateTagName_ChineseName() {
        // Given
        String name = "数据库";

        // When & Then
        assertDoesNotThrow(() -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - null 名称")
    void testValidateTagName_NullName() {
        // Given
        String name = null;

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 仅空格名称")
    void testValidateTagName_OnlySpaces() {
        // Given
        String name = "     ";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 恰好50字符")
    void testValidateTagName_Exactly50Chars() {
        // Given
        String name = "a".repeat(50);

        // When & Then
        assertDoesNotThrow(() -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 包含连字符和下划线")
    void testValidateTagName_WithHyphenAndUnderscore() {
        // Given
        String name = "Spring-Boot_Framework";

        // When & Then
        assertDoesNotThrow(() -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 混合中英文")
    void testValidateTagName_MixedChineseEnglish() {
        // Given
        String name = "Spring框架";

        // When & Then
        assertDoesNotThrow(() -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 包含数字")
    void testValidateTagName_WithNumbers() {
        // Given
        String name = "Java8";

        // When & Then
        assertDoesNotThrow(() -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 多种非法字符")
    void testValidateTagName_MultipleIllegalChars() {
        // Given
        String name = "Tag!@#$%^&*()+=[]{}|;':\",./<>?";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 包含换行符（允许）")
    void testValidateTagName_WithNewline() {
        // Given
        String name = "Tag\nName";

        // When & Then
        // 注意：当前实现允许空白字符（包括换行符），因为正则表达式中包含 \s
        assertDoesNotThrow(() -> {
            tagDomainService.validateTagName(name);
        });
    }

    @Test
    @DisplayName("测试 Tag 验证 - 包含制表符（允许）")
    void testValidateTagName_WithTab() {
        // Given
        String name = "Tag\tName";

        // When & Then
        // 注意：当前实现允许空白字符（包括制表符），因为正则表达式中包含 \s
        assertDoesNotThrow(() -> {
            tagDomainService.validateTagName(name);
        });
    }

    // ==================== findOrCreate 测试 ====================

    @Test
    @DisplayName("测试 findOrCreate - 标签已存在")
    void testFindOrCreate_TagExists() {
        // Given
        String name = "Java";
        String slug = "java";
        Tag existingTag = Tag.create(1L, name, slug);
        when(tagRepository.findBySlug(slug)).thenReturn(Optional.of(existingTag));

        // When
        Tag result = tagDomainService.findOrCreate(name);

        // Then
        assertNotNull(result);
        assertEquals(existingTag.getId(), result.getId());
        assertEquals(existingTag.getName(), result.getName());
        verify(tagRepository, times(1)).findBySlug(slug);
        verify(tagRepository, never()).save(any());
    }

    @Test
    @DisplayName("测试 findOrCreate - 创建新标签")
    void testFindOrCreate_CreateNewTag() {
        // Given
        String name = "Java";
        String slug = "java";
        Tag newTag = Tag.create(1L, name, slug);
        when(tagRepository.findBySlug(slug)).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(newTag);

        // When
        Tag result = tagDomainService.findOrCreate(name);

        // Then
        assertNotNull(result);
        assertEquals(newTag.getId(), result.getId());
        assertEquals(newTag.getName(), result.getName());
        verify(tagRepository, times(1)).findBySlug(slug);
        verify(tagRepository, times(1)).save(any(Tag.class));
    }

    @Test
    @DisplayName("测试 findOrCreate - 无效名称")
    void testFindOrCreate_InvalidName() {
        // Given
        String name = "";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            tagDomainService.findOrCreate(name);
        });
    }

    // ==================== findOrCreateBatch 测试 ====================

    @Test
    @DisplayName("测试 findOrCreateBatch - 批量创建")
    void testFindOrCreateBatch() {
        // Given
        List<String> names = Arrays.asList("Java", "Spring", "PostgreSQL");
        Tag tag1 = Tag.create(1L, "Java", "java");
        Tag tag2 = Tag.create(2L, "Spring", "spring");
        Tag tag3 = Tag.create(3L, "PostgreSQL", "postgresql");

        when(tagRepository.findBySlug("java")).thenReturn(Optional.of(tag1));
        when(tagRepository.findBySlug("spring")).thenReturn(Optional.empty());
        when(tagRepository.findBySlug("postgresql")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class)))
                .thenReturn(tag2)
                .thenReturn(tag3);

        // When
        List<Tag> results = tagDomainService.findOrCreateBatch(names);

        // Then
        assertNotNull(results);
        assertEquals(3, results.size());
        verify(tagRepository, times(3)).findBySlug(anyString());
        verify(tagRepository, times(2)).save(any(Tag.class));
    }

    @Test
    @DisplayName("测试 findOrCreateBatch - 空列表")
    void testFindOrCreateBatch_EmptyList() {
        // Given
        List<String> names = Arrays.asList();

        // When
        List<Tag> results = tagDomainService.findOrCreateBatch(names);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(tagRepository, never()).findBySlug(anyString());
        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    @DisplayName("测试 findOrCreateBatch - 去重")
    void testFindOrCreateBatch_Deduplication() {
        // Given
        List<String> names = Arrays.asList("Java", "Java", "Spring");
        Tag tag1 = Tag.create(1L, "Java", "java");
        Tag tag2 = Tag.create(2L, "Spring", "spring");

        when(tagRepository.findBySlug("java")).thenReturn(Optional.of(tag1));
        when(tagRepository.findBySlug("spring")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(tag2);

        // When
        List<Tag> results = tagDomainService.findOrCreateBatch(names);

        // Then
        assertNotNull(results);
        assertEquals(2, results.size());
        verify(tagRepository, times(2)).findBySlug(anyString());
        verify(tagRepository, times(1)).save(any(Tag.class));
    }
}
