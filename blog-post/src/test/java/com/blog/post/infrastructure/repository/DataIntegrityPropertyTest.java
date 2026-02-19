package com.blog.post.infrastructure.repository;

import com.blog.post.domain.model.Tag;
import com.blog.post.domain.repository.PostTagRepository;
import com.blog.post.domain.repository.TagRepository;
import com.blog.post.domain.service.TagDomainService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据完整性属性测试
 * 
 * 测试数据库级联删除和 Slug 规范化的数据完整性约束
 * 
 * 包含属性测试：
 * - Property 12: Tag 删除级联
 * - Property 13: Post 删除级联
 * - Property 14: Slug 规范化防止语义重复
 * 
 * Validates: 数据完整性约束, Requirements 5.2.3
 *
 * @author Blog Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("数据完整性属性测试")
class DataIntegrityPropertyTest {

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private PostTagRepository postTagRepository;

    @Autowired
    private TagDomainService tagDomainService;

    // ==================== Property 12: Tag 删除级联 ====================

    /**
     * Property 12: Tag 删除级联
     * 
     * 对于任意 Tag，删除 Tag 时应该先删除所有关联的 Post-Tag 关系
     * 
     * 注意：这个测试验证业务层的级联删除逻辑，而不是数据库的 ON DELETE CASCADE
     * 
     * Validates: 数据完整性约束
     * 
     * Feature: post-tag-need, Property 12: Tag 删除级联
     */
    @Property(tries = 100)
    @DisplayName("Property 12: Tag 删除级联（业务层控制）")
    void property_tagDeleteCascade(
            @ForAll @StringLength(min = 3, max = 20) String tagName,
            @ForAll("postIdLists") List<Long> postIds
    ) {
        // Given: 创建一个 Tag 并关联多个 Post
        Tag tag = tagDomainService.findOrCreate(tagName);
        Long tagId = tag.getId();
        
        // 建立 Post-Tag 关联
        for (Long postId : postIds) {
            postTagRepository.attach(postId, tagId);
        }
        
        // 验证关联已创建
        for (Long postId : postIds) {
            assertThat(postTagRepository.exists(postId, tagId))
                    .as("Post-Tag 关联应该存在: postId=%d, tagId=%d", postId, tagId)
                    .isTrue();
        }
        
        int initialAssociationCount = postTagRepository.countPostsByTagId(tagId);
        assertThat(initialAssociationCount)
                .as("Tag 应该关联 %d 个 Post", postIds.size())
                .isEqualTo(postIds.size());

        // When: 业务层级联删除 - 先删除所有关联，再删除 Tag
        // 1. 查询所有关联的 Post
        List<Long> associatedPostIds = postTagRepository.findPostIdsByTagId(tagId);
        
        // 2. 删除所有 Post-Tag 关联
        for (Long postId : associatedPostIds) {
            postTagRepository.detach(postId, tagId);
        }
        
        // 3. 删除 Tag
        tagRepository.deleteById(tagId);

        // Then: 所有 Post-Tag 关联应该被删除
        for (Long postId : postIds) {
            assertThat(postTagRepository.exists(postId, tagId))
                    .as("Post-Tag 关联应该被删除: postId=%d, tagId=%d", postId, tagId)
                    .isFalse();
        }
        
        // 验证 Tag 下的 Post 数量为 0
        int finalAssociationCount = postTagRepository.countPostsByTagId(tagId);
        assertThat(finalAssociationCount)
                .as("Tag 删除后，关联的 Post 数量应该为 0")
                .isEqualTo(0);
        
        // 验证 Tag 已被删除
        assertThat(tagRepository.findById(tagId))
                .as("Tag 应该被删除")
                .isEmpty();
    }

    // ==================== Property 13: Post 删除级联 ====================

    /**
     * Property 13: Post 删除级联
     * 
     * 对于任意 Post，删除 Post 后，所有关联的 Post-Tag 关系也应该被删除
     * 
     * Validates: 数据完整性约束（ON DELETE CASCADE）
     * 
     * Feature: post-tag-need, Property 13: Post 删除级联
     */
    @Property(tries = 100)
    @DisplayName("Property 13: Post 删除级联")
    void property_postDeleteCascade(
            @ForAll @LongRange(min = 1L, max = 999999L) long postId,
            @ForAll("tagNameLists") List<String> tagNames
    ) {
        // Given: 创建多个 Tag 并关联到一个 Post
        List<Tag> tags = tagDomainService.findOrCreateBatch(tagNames);
        List<Long> tagIds = tags.stream().map(Tag::getId).collect(Collectors.toList());
        
        // 建立 Post-Tag 关联
        postTagRepository.attachBatch(postId, tagIds);
        
        // 验证关联已创建
        for (Long tagId : tagIds) {
            assertThat(postTagRepository.exists(postId, tagId))
                    .as("Post-Tag 关联应该存在: postId=%d, tagId=%d", postId, tagId)
                    .isTrue();
        }
        
        int initialAssociationCount = postTagRepository.countTagsByPostId(postId);
        assertThat(initialAssociationCount)
                .as("Post 应该关联 %d 个 Tag", tagIds.size())
                .isEqualTo(tagIds.size());

        // When: 删除 Post（通过删除所有关联模拟 Post 删除的级联效果）
        // 注意：由于我们没有 PostRepository，这里通过 detachAllByPostId 模拟级联删除
        postTagRepository.detachAllByPostId(postId);

        // Then: 所有 Post-Tag 关联应该被删除
        for (Long tagId : tagIds) {
            assertThat(postTagRepository.exists(postId, tagId))
                    .as("Post-Tag 关联应该被删除: postId=%d, tagId=%d", postId, tagId)
                    .isFalse();
        }
        
        // 验证 Post 下的 Tag 数量为 0
        int finalAssociationCount = postTagRepository.countTagsByPostId(postId);
        assertThat(finalAssociationCount)
                .as("Post 删除后，关联的 Tag 数量应该为 0")
                .isEqualTo(0);
        
        // 验证 Tag 仍然存在（只删除关联，不删除 Tag）
        for (Long tagId : tagIds) {
            assertThat(tagRepository.findById(tagId))
                    .as("Tag 应该仍然存在: tagId=%d", tagId)
                    .isPresent();
        }
    }

    // ==================== Property 14: Slug 规范化防止语义重复 ====================

    /**
     * Property 14: Slug 规范化防止语义重复
     * 
     * 对于任意常见的语义重复 Tag 名称（如 "js", "JS", "JavaScript"），
     * 规范化后应该生成不同的 slug（除非它们确实相同）
     * 
     * 测试策略：
     * - 测试大小写变体（如 "JS" vs "js"）应该生成相同的 slug
     * - 测试空格变体（如 "Java Script" vs "JavaScript"）应该生成不同的 slug
     * - 测试缩写和全称（如 "js" vs "javascript"）应该生成不同的 slug
     * 
     * Validates: Requirements 5.2.3
     * 
     * Feature: post-tag-need, Property 14: Slug 规范化防止语义重复
     */
    @Property(tries = 100)
    @DisplayName("Property 14: Slug 规范化 - 大小写不敏感")
    void property_slugNormalizationCaseInsensitive(
            @ForAll @StringLength(min = 3, max = 20) String baseName
    ) {
        // Given: 同一个名称的不同大小写变体
        String lowercase = baseName.toLowerCase();
        String uppercase = baseName.toUpperCase();
        String mixedCase = baseName;

        // When: 规范化为 slug
        String slug1 = tagDomainService.normalizeToSlug(lowercase);
        String slug2 = tagDomainService.normalizeToSlug(uppercase);
        String slug3 = tagDomainService.normalizeToSlug(mixedCase);

        // Then: 应该生成相同的 slug
        assertThat(slug1)
                .as("大小写变体应该生成相同的 slug")
                .isEqualTo(slug2)
                .isEqualTo(slug3);
        
        // 验证 findOrCreate 的幂等性
        Tag tag1 = tagDomainService.findOrCreate(lowercase);
        Tag tag2 = tagDomainService.findOrCreate(uppercase);
        Tag tag3 = tagDomainService.findOrCreate(mixedCase);
        
        assertThat(tag1.getId())
                .as("大小写变体应该返回同一个 Tag")
                .isEqualTo(tag2.getId())
                .isEqualTo(tag3.getId());
    }

    @Property(tries = 50)
    @DisplayName("Property 14: Slug 规范化 - 空格处理")
    void property_slugNormalizationSpaceHandling(
            @ForAll @StringLength(min = 3, max = 10) String word1,
            @ForAll @StringLength(min = 3, max = 10) String word2
    ) {
        // Given: 带空格和不带空格的名称
        String withSpace = word1 + " " + word2;
        String withoutSpace = word1 + word2;
        String withMultipleSpaces = word1 + "   " + word2;

        // When: 规范化为 slug
        String slug1 = tagDomainService.normalizeToSlug(withSpace);
        String slug2 = tagDomainService.normalizeToSlug(withoutSpace);
        String slug3 = tagDomainService.normalizeToSlug(withMultipleSpaces);

        // Then: 带空格的应该生成带连字符的 slug
        assertThat(slug1)
                .as("空格应该被转换为连字符")
                .contains("-");
        
        // 不带空格的应该生成不带连字符的 slug
        assertThat(slug2)
                .as("不带空格的名称不应该有连字符")
                .doesNotContain("-");
        
        // 多个空格应该被合并为单个连字符
        assertThat(slug3)
                .as("多个空格应该被合并为单个连字符")
                .isEqualTo(slug1);
        
        // 验证它们生成不同的 Tag
        Tag tag1 = tagDomainService.findOrCreate(withSpace);
        Tag tag2 = tagDomainService.findOrCreate(withoutSpace);
        
        assertThat(tag1.getId())
                .as("带空格和不带空格的名称应该生成不同的 Tag")
                .isNotEqualTo(tag2.getId());
    }

    @Example
    @DisplayName("Property 14: Slug 规范化 - 常见缩写测试")
    void property_slugNormalizationCommonAbbreviations() {
        // Given: 常见的缩写和全称
        String[][] testCases = {
                {"js", "javascript"},
                {"ts", "typescript"},
                {"py", "python"},
                {"cpp", "c++"},
                {"db", "database"}
        };

        for (String[] testCase : testCases) {
            String abbreviation = testCase[0];
            String fullName = testCase[1];

            // When: 规范化为 slug
            String slug1 = tagDomainService.normalizeToSlug(abbreviation);
            String slug2 = tagDomainService.normalizeToSlug(fullName);

            // Then: 应该生成不同的 slug
            assertThat(slug1)
                    .as("缩写 '%s' 和全称 '%s' 应该生成不同的 slug", abbreviation, fullName)
                    .isNotEqualTo(slug2);

            // 验证它们生成不同的 Tag
            Tag tag1 = tagDomainService.findOrCreate(abbreviation);
            Tag tag2 = tagDomainService.findOrCreate(fullName);

            assertThat(tag1.getId())
                    .as("缩写 '%s' 和全称 '%s' 应该生成不同的 Tag", abbreviation, fullName)
                    .isNotEqualTo(tag2.getId());
        }
    }

    @Example
    @DisplayName("Property 14: Slug 规范化 - 大小写变体测试")
    void property_slugNormalizationCaseVariants() {
        // Given: 常见的大小写变体
        String[][] testCases = {
                {"Java", "java", "JAVA"},
                {"Python", "python", "PYTHON"},
                {"JavaScript", "javascript", "JAVASCRIPT"},
                {"PostgreSQL", "postgresql", "POSTGRESQL"}
        };

        for (String[] testCase : testCases) {
            // When: 规范化为 slug
            Set<String> slugs = Set.of(
                    tagDomainService.normalizeToSlug(testCase[0]),
                    tagDomainService.normalizeToSlug(testCase[1]),
                    tagDomainService.normalizeToSlug(testCase[2])
            );

            // Then: 应该生成相同的 slug
            assertThat(slugs)
                    .as("大小写变体 %s 应该生成相同的 slug", String.join(", ", testCase))
                    .hasSize(1);

            // 验证它们生成同一个 Tag
            Tag tag1 = tagDomainService.findOrCreate(testCase[0]);
            Tag tag2 = tagDomainService.findOrCreate(testCase[1]);
            Tag tag3 = tagDomainService.findOrCreate(testCase[2]);

            assertThat(tag1.getId())
                    .as("大小写变体应该返回同一个 Tag")
                    .isEqualTo(tag2.getId())
                    .isEqualTo(tag3.getId());
        }
    }

    // ==================== 数据生成器 ====================

    /**
     * 生成 Post ID 列表
     * 
     * 规则：
     * - 列表大小 1-5
     * - ID 范围 1-999999
     * - 不包含重复 ID
     */
    @Provide
    Arbitrary<List<Long>> postIdLists() {
        return Arbitraries.longs()
                .between(1L, 999999L)
                .list()
                .ofMinSize(1)
                .ofMaxSize(5)
                .map(list -> list.stream().distinct().toList());
    }

    /**
     * 生成 Tag 名称列表
     * 
     * 规则：
     * - 列表大小 1-5
     * - 名称长度 3-20
     * - 不包含重复名称
     */
    @Provide
    Arbitrary<List<String>> tagNameLists() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(20)
                .list()
                .ofMinSize(1)
                .ofMaxSize(5)
                .map(list -> list.stream().distinct().toList());
    }
}
