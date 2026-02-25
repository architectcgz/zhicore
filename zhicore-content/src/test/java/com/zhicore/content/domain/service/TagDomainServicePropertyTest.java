package com.zhicore.content.domain.service;

import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.infrastructure.service.TagDomainServiceImpl;
import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.result.ApiResponse;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TagDomainService 属性测试
 * 使用 jqwik 进行基于属性的测试
 * 
 * Feature: post-tag-need
 * 
 * @author ZhiCore Team
 */
class TagDomainServicePropertyTest {

    // 为每个测试创建新的服务实例
    private TagDomainService createService() {
        TagRepository mockRepository = Mockito.mock(TagRepository.class);
        IdGeneratorFeignClient mockIdClient = Mockito.mock(IdGeneratorFeignClient.class);
        Mockito.when(mockIdClient.generateSnowflakeId()).thenReturn(ApiResponse.success(1L));
        return new TagDomainServiceImpl(mockRepository, mockIdClient);
    }

    // ==================== Property 2: Slug 规范化一致性 ====================

    /**
     * Property 2: Slug 规范化一致性
     * 
     * 对于任意 Tag 名称，经过规范化处理后生成的 slug 应该是确定的、可重复的
     * 
     * Feature: post-tag-need, Property 2: Slug 规范化一致性
     * Validates: Requirements 4.1.2, 4.1.3, 4.2.2
     */
    @Property(tries = 100)
    @Label("Property 2: Slug 规范化一致性 - 对同一名称多次规范化应返回相同结果")
    void slugNormalizationIsConsistent(
            @ForAll @NotBlank @StringLength(min = 1, max = 50) @AlphaChars String tagName) {
        
        // 跳过纯空白字符的情况
        if (tagName.trim().isEmpty()) {
            return;
        }
        
        TagDomainService service = createService();
        
        try {
            // When: 对同一个 Tag 名称多次调用 normalizeToSlug()
            String slug1 = service.normalizeToSlug(tagName);
            String slug2 = service.normalizeToSlug(tagName);
            String slug3 = service.normalizeToSlug(tagName);
            
            // Then: 验证每次返回的 slug 都相同
            assertEquals(slug1, slug2, 
                "第一次和第二次规范化结果应该相同: tagName=" + tagName);
            assertEquals(slug2, slug3, 
                "第二次和第三次规范化结果应该相同: tagName=" + tagName);
            assertEquals(slug1, slug3, 
                "第一次和第三次规范化结果应该相同: tagName=" + tagName);
            
            // 验证 slug 格式正确
            assertNotNull(slug1, "Slug 不应为 null");
            assertFalse(slug1.isEmpty(), "Slug 不应为空");
            assertTrue(slug1.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"), 
                "Slug 应该符合格式规范: " + slug1);
            
        } catch (IllegalArgumentException e) {
            // 如果抛出异常，验证是合理的（例如空名称）
            assertTrue(tagName.trim().isEmpty() || tagName.length() > 50,
                "只有空名称或超长名称才应该抛出异常");
        }
    }

    // ==================== Property 3: 大小写和空格不敏感 ====================

    /**
     * Property 3: 大小写和空格不敏感
     * 
     * 对于任意 Tag 名称的不同大小写和空格组合，它们应该生成相同的 slug
     * 
     * Feature: post-tag-need, Property 3: 大小写和空格不敏感
     * Validates: Requirements 4.1.3
     */
    @Property(tries = 100)
    @Label("Property 3: 大小写和空格不敏感 - 不同大小写和空格组合应生成相同 slug")
    void slugNormalizationIsCaseAndSpaceInsensitive(
            @ForAll @NotBlank @StringLength(min = 1, max = 50) @AlphaChars String baseTagName) {
        
        // 跳过纯空白字符的情况
        if (baseTagName.trim().isEmpty()) {
            return;
        }
        
        TagDomainService service = createService();
        
        try {
            // Given: 生成不同的大小写和空格变体
            String lowercase = baseTagName.toLowerCase();
            String uppercase = baseTagName.toUpperCase();
            String withLeadingSpace = " " + baseTagName;
            String withTrailingSpace = baseTagName + " ";
            String withBothSpaces = " " + baseTagName + " ";
            String withMultipleSpaces = baseTagName.replace("", " ").trim();
            
            // When: 对所有变体进行规范化
            String slugBase = service.normalizeToSlug(baseTagName);
            String slugLower = service.normalizeToSlug(lowercase);
            String slugUpper = service.normalizeToSlug(uppercase);
            String slugLeading = service.normalizeToSlug(withLeadingSpace);
            String slugTrailing = service.normalizeToSlug(withTrailingSpace);
            String slugBoth = service.normalizeToSlug(withBothSpaces);
            
            // Then: 验证所有变体生成相同的 slug
            assertEquals(slugBase, slugLower, 
                "小写变体应生成相同 slug: base=" + baseTagName + ", lower=" + lowercase);
            assertEquals(slugBase, slugUpper, 
                "大写变体应生成相同 slug: base=" + baseTagName + ", upper=" + uppercase);
            assertEquals(slugBase, slugLeading, 
                "前导空格变体应生成相同 slug: base=" + baseTagName);
            assertEquals(slugBase, slugTrailing, 
                "尾随空格变体应生成相同 slug: base=" + baseTagName);
            assertEquals(slugBase, slugBoth, 
                "前后空格变体应生成相同 slug: base=" + baseTagName);
            
            // 验证 slug 是小写的
            assertEquals(slugBase.toLowerCase(), slugBase, 
                "Slug 应该是小写的: " + slugBase);
            
        } catch (IllegalArgumentException e) {
            // 如果抛出异常，验证是合理的
            assertTrue(baseTagName.trim().isEmpty() || baseTagName.length() > 50,
                "只有空名称或超长名称才应该抛出异常");
        }
    }

    /**
     * Property 3 扩展: 测试混合大小写和空格的复杂场景
     * 
     * Feature: post-tag-need, Property 3: 大小写和空格不敏感
     * Validates: Requirements 4.1.3, 4.2.2
     */
    @Property(tries = 100)
    @Label("Property 3 扩展: 混合大小写和空格的复杂场景")
    void slugNormalizationHandlesComplexCaseAndSpaceVariations(
            @ForAll @NotBlank @StringLength(min = 2, max = 30) String word1,
            @ForAll @NotBlank @StringLength(min = 2, max = 30) String word2) {
        
        // 跳过纯空白字符的情况
        if (word1.trim().isEmpty() || word2.trim().isEmpty()) {
            return;
        }
        
        // 只使用字母字符
        String cleanWord1 = word1.replaceAll("[^a-zA-Z]", "");
        String cleanWord2 = word2.replaceAll("[^a-zA-Z]", "");
        
        if (cleanWord1.isEmpty() || cleanWord2.isEmpty()) {
            return;
        }
        
        TagDomainService service = createService();
        
        try {
            // Given: 创建不同的组合
            String normal = cleanWord1 + " " + cleanWord2;
            String mixedCase1 = cleanWord1.toUpperCase() + " " + cleanWord2.toLowerCase();
            String mixedCase2 = cleanWord1.toLowerCase() + " " + cleanWord2.toUpperCase();
            String multiSpace = cleanWord1 + "   " + cleanWord2;
            String withTabs = cleanWord1 + "\t" + cleanWord2;
            
            // When: 规范化所有变体
            String slugNormal = service.normalizeToSlug(normal);
            String slugMixed1 = service.normalizeToSlug(mixedCase1);
            String slugMixed2 = service.normalizeToSlug(mixedCase2);
            String slugMulti = service.normalizeToSlug(multiSpace);
            String slugTabs = service.normalizeToSlug(withTabs);
            
            // Then: 所有变体应生成相同的 slug
            assertEquals(slugNormal, slugMixed1, 
                "混合大小写1应生成相同 slug: " + normal + " vs " + mixedCase1);
            assertEquals(slugNormal, slugMixed2, 
                "混合大小写2应生成相同 slug: " + normal + " vs " + mixedCase2);
            assertEquals(slugNormal, slugMulti, 
                "多个空格应生成相同 slug: " + normal + " vs " + multiSpace);
            assertEquals(slugNormal, slugTabs, 
                "制表符应生成相同 slug: " + normal + " vs " + withTabs);
            
            // 验证 slug 格式
            assertTrue(slugNormal.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"), 
                "Slug 应该符合格式规范: " + slugNormal);
            
        } catch (IllegalArgumentException e) {
            // 允许合理的异常
            String combined = cleanWord1 + cleanWord2;
            assertTrue(combined.length() > 50, 
                "只有超长名称才应该抛出异常");
        }
    }

    /**
     * Property 2 扩展: 测试特殊字符处理的一致性
     * 
     * Feature: post-tag-need, Property 2: Slug 规范化一致性
     * Validates: Requirements 4.1.2, 4.2.2
     */
    @Property(tries = 100)
    @Label("Property 2 扩展: 特殊字符处理的一致性")
    void slugNormalizationHandlesSpecialCharactersConsistently(
            @ForAll @NotBlank @StringLength(min = 1, max = 30) @AlphaChars String baseWord) {
        
        if (baseWord.trim().isEmpty()) {
            return;
        }
        
        TagDomainService service = createService();
        
        try {
            // Given: 添加各种特殊字符
            String withPlus = baseWord + "++";
            String withHash = baseWord + "##";
            String withDash = baseWord + "--";
            String withUnderscore = baseWord + "__";
            
            // When: 规范化
            String slugBase = service.normalizeToSlug(baseWord);
            String slugPlus = service.normalizeToSlug(withPlus);
            String slugHash = service.normalizeToSlug(withHash);
            String slugDash = service.normalizeToSlug(withDash);
            String slugUnderscore = service.normalizeToSlug(withUnderscore);
            
            // Then: 验证特殊字符被正确过滤
            assertNotNull(slugBase);
            assertNotNull(slugPlus);
            assertNotNull(slugHash);
            assertNotNull(slugDash);
            assertNotNull(slugUnderscore);
            
            // 所有 slug 应该只包含小写字母、数字和连字符
            assertTrue(slugBase.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"), 
                "Base slug 应符合格式: " + slugBase);
            assertTrue(slugPlus.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"), 
                "Plus slug 应符合格式: " + slugPlus);
            assertTrue(slugHash.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"), 
                "Hash slug 应符合格式: " + slugHash);
            assertTrue(slugDash.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"), 
                "Dash slug 应符合格式: " + slugDash);
            assertTrue(slugUnderscore.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$"), 
                "Underscore slug 应符合格式: " + slugUnderscore);
            
        } catch (IllegalArgumentException e) {
            // 允许合理的异常（例如结果为空）
            // 这是预期的行为
        }
    }

    // ==================== Property 6: Tag 自动创建幂等性 ====================

    /**
     * Property 6: Tag 自动创建幂等性
     * 
     * 对于任意 Tag 名称，多次调用 findOrCreate 应该返回同一个 Tag（id 相同）
     * 
     * Feature: post-tag-need, Property 6: Tag 自动创建幂等性
     * Validates: Requirements 4.2.3
     */
    @Property(tries = 100)
    @Label("Property 6: Tag 自动创建幂等性 - 多次调用 findOrCreate 应返回相同 Tag")
    void findOrCreateIsIdempotent(
            @ForAll @NotBlank @StringLength(min = 1, max = 50) @AlphaChars String tagName) {
        
        // 跳过纯空白字符的情况
        if (tagName.trim().isEmpty()) {
            return;
        }
        
        // 创建 mock repository
        TagRepository mockRepository = Mockito.mock(TagRepository.class);
        IdGeneratorFeignClient mockIdClient = Mockito.mock(IdGeneratorFeignClient.class);
        Mockito.when(mockIdClient.generateSnowflakeId()).thenReturn(ApiResponse.success(11111L));
        TagDomainService service = new TagDomainServiceImpl(mockRepository, mockIdClient);
        
        try {
            // 规范化 slug
            String slug = service.normalizeToSlug(tagName);
            
            // 创建一个 Tag 实例用于模拟返回
            Long tagId = 12345L;
            Tag mockTag = Tag.create(tagId, tagName.trim(), slug);
            
            // 配置 mock：第一次查询返回空，保存后返回 mockTag，后续查询返回 mockTag
            Mockito.when(mockRepository.findBySlug(slug))
                .thenReturn(Optional.empty())  // 第一次查询：不存在
                .thenReturn(Optional.of(mockTag));  // 后续查询：存在
            
            Mockito.when(mockRepository.save(Mockito.any(Tag.class)))
                .thenReturn(mockTag);
            
            // When: 对同一个 Tag 名称多次调用 findOrCreate()
            Tag tag1 = service.findOrCreate(tagName);
            Tag tag2 = service.findOrCreate(tagName);
            Tag tag3 = service.findOrCreate(tagName);
            
            // Then: 验证返回的 Tag id 都相同（幂等性）
            assertNotNull(tag1, "第一次调用应返回 Tag");
            assertNotNull(tag2, "第二次调用应返回 Tag");
            assertNotNull(tag3, "第三次调用应返回 Tag");
            
            assertEquals(tag1.getId(), tag2.getId(), 
                "第一次和第二次调用应返回相同 ID 的 Tag: tagName=" + tagName);
            assertEquals(tag2.getId(), tag3.getId(), 
                "第二次和第三次调用应返回相同 ID 的 Tag: tagName=" + tagName);
            assertEquals(tag1.getId(), tag3.getId(), 
                "第一次和第三次调用应返回相同 ID 的 Tag: tagName=" + tagName);
            
            // 验证 slug 一致
            assertEquals(tag1.getSlug(), tag2.getSlug(), 
                "所有返回的 Tag 应有相同的 slug");
            assertEquals(tag2.getSlug(), tag3.getSlug(), 
                "所有返回的 Tag 应有相同的 slug");
            
            // 验证 save 只被调用一次（第一次创建时）
            Mockito.verify(mockRepository, Mockito.times(1))
                .save(Mockito.any(Tag.class));
            
            // 验证 findBySlug 被调用多次（每次 findOrCreate 都会查询）
            Mockito.verify(mockRepository, Mockito.atLeast(2))
                .findBySlug(slug);
            
        } catch (IllegalArgumentException e) {
            // 如果抛出异常，验证是合理的（例如空名称或超长名称）
            assertTrue(tagName.trim().isEmpty() || tagName.length() > 50,
                "只有空名称或超长名称才应该抛出异常");
        }
    }

    /**
     * Property 6 扩展: 测试不同大小写和空格变体的幂等性
     * 
     * 对于任意 Tag 名称的不同大小写和空格组合，它们应该返回相同的 Tag
     * 
     * Feature: post-tag-need, Property 6: Tag 自动创建幂等性
     * Validates: Requirements 4.2.3, 4.1.3
     */
    @Property(tries = 100)
    @Label("Property 6 扩展: 不同大小写和空格变体的幂等性")
    void findOrCreateIsIdempotentAcrossCaseAndSpaceVariations(
            @ForAll @NotBlank @StringLength(min = 1, max = 50) @AlphaChars String baseTagName) {
        
        // 跳过纯空白字符的情况
        if (baseTagName.trim().isEmpty()) {
            return;
        }
        
        // 创建 mock repository
        TagRepository mockRepository = Mockito.mock(TagRepository.class);
        IdGeneratorFeignClient mockIdClient = Mockito.mock(IdGeneratorFeignClient.class);
        Mockito.when(mockIdClient.generateSnowflakeId()).thenReturn(ApiResponse.success(67890L));
        TagDomainService service = new TagDomainServiceImpl(mockRepository, mockIdClient);
        
        try {
            // 规范化 slug
            String slug = service.normalizeToSlug(baseTagName);
            
            // 创建一个 Tag 实例用于模拟返回
            Long tagId = 67890L;
            Tag mockTag = Tag.create(tagId, baseTagName.trim(), slug);
            
            // 配置 mock：第一次查询返回空，保存后返回 mockTag，后续查询返回 mockTag
            Mockito.when(mockRepository.findBySlug(slug))
                .thenReturn(Optional.empty())  // 第一次查询：不存在
                .thenReturn(Optional.of(mockTag))  // 后续查询：存在
                .thenReturn(Optional.of(mockTag))
                .thenReturn(Optional.of(mockTag))
                .thenReturn(Optional.of(mockTag));
            
            Mockito.when(mockRepository.save(Mockito.any(Tag.class)))
                .thenReturn(mockTag);
            
            // Given: 生成不同的大小写和空格变体
            String lowercase = baseTagName.toLowerCase();
            String uppercase = baseTagName.toUpperCase();
            String withSpaces = " " + baseTagName + " ";
            String mixedCase = baseTagName.length() > 1 
                ? baseTagName.substring(0, 1).toUpperCase() + baseTagName.substring(1).toLowerCase()
                : baseTagName.toUpperCase();
            
            // When: 对所有变体调用 findOrCreate
            Tag tag1 = service.findOrCreate(baseTagName);
            Tag tag2 = service.findOrCreate(lowercase);
            Tag tag3 = service.findOrCreate(uppercase);
            Tag tag4 = service.findOrCreate(withSpaces);
            Tag tag5 = service.findOrCreate(mixedCase);
            
            // Then: 验证所有变体返回相同 ID 的 Tag
            assertNotNull(tag1, "基础名称应返回 Tag");
            assertNotNull(tag2, "小写变体应返回 Tag");
            assertNotNull(tag3, "大写变体应返回 Tag");
            assertNotNull(tag4, "空格变体应返回 Tag");
            assertNotNull(tag5, "混合大小写变体应返回 Tag");
            
            assertEquals(tag1.getId(), tag2.getId(), 
                "小写变体应返回相同 ID: base=" + baseTagName + ", lower=" + lowercase);
            assertEquals(tag1.getId(), tag3.getId(), 
                "大写变体应返回相同 ID: base=" + baseTagName + ", upper=" + uppercase);
            assertEquals(tag1.getId(), tag4.getId(), 
                "空格变体应返回相同 ID: base=" + baseTagName);
            assertEquals(tag1.getId(), tag5.getId(), 
                "混合大小写变体应返回相同 ID: base=" + baseTagName);
            
            // 验证所有 Tag 的 slug 都相同
            assertEquals(tag1.getSlug(), tag2.getSlug(), "所有变体应有相同 slug");
            assertEquals(tag1.getSlug(), tag3.getSlug(), "所有变体应有相同 slug");
            assertEquals(tag1.getSlug(), tag4.getSlug(), "所有变体应有相同 slug");
            assertEquals(tag1.getSlug(), tag5.getSlug(), "所有变体应有相同 slug");
            
            // 验证 save 只被调用一次
            Mockito.verify(mockRepository, Mockito.times(1))
                .save(Mockito.any(Tag.class));
            
        } catch (IllegalArgumentException e) {
            // 允许合理的异常
            assertTrue(baseTagName.trim().isEmpty() || baseTagName.length() > 50,
                "只有空名称或超长名称才应该抛出异常");
        }
    }

    /**
     * Property 6 扩展: 测试并发场景下的幂等性
     * 
     * 模拟并发创建相同 Tag 的场景，验证系统能正确处理唯一索引冲突
     * 
     * Feature: post-tag-need, Property 6: Tag 自动创建幂等性
     * Validates: Requirements 4.2.3
     */
    @Property(tries = 100)
    @Label("Property 6 扩展: 并发场景下的幂等性")
    void findOrCreateHandlesConcurrentCreation(
            @ForAll @NotBlank @StringLength(min = 1, max = 50) @AlphaChars String tagName) {
        
        // 跳过纯空白字符的情况
        if (tagName.trim().isEmpty()) {
            return;
        }
        
        // 创建 mock repository
        TagRepository mockRepository = Mockito.mock(TagRepository.class);
        IdGeneratorFeignClient mockIdClient = Mockito.mock(IdGeneratorFeignClient.class);
        Mockito.when(mockIdClient.generateSnowflakeId()).thenReturn(ApiResponse.success(11111L));
        TagDomainService service = new TagDomainServiceImpl(mockRepository, mockIdClient);
        
        try {
            // 规范化 slug
            String slug = service.normalizeToSlug(tagName);
            
            // 创建一个 Tag 实例用于模拟返回
            Long tagId = 11111L;
            Tag mockTag = Tag.create(tagId, tagName.trim(), slug);
            
            // 配置 mock：模拟并发场景
            // 第一次查询返回空（两个线程都认为不存在）
            // 第一次保存抛出 DataIntegrityViolationException（唯一索引冲突）
            // 重试查询返回已存在的 Tag
            Mockito.when(mockRepository.findBySlug(slug))
                .thenReturn(Optional.empty())  // 第一次查询：不存在
                .thenReturn(Optional.of(mockTag));  // 重试查询：存在
            
            Mockito.when(mockRepository.save(Mockito.any(Tag.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                    "Duplicate entry for slug: " + slug));
            
            // When: 调用 findOrCreate（模拟并发冲突场景）
            Tag result = service.findOrCreate(tagName);
            
            // Then: 应该成功返回 Tag（通过重试查询获得）
            assertNotNull(result, "并发冲突场景应返回 Tag");
            assertEquals(tagId, result.getId(), 
                "并发冲突场景应返回正确的 Tag ID");
            assertEquals(slug, result.getSlug(), 
                "并发冲突场景应返回正确的 slug");
            
            // 验证 save 被调用一次（尝试创建）
            Mockito.verify(mockRepository, Mockito.times(1))
                .save(Mockito.any(Tag.class));
            
            // 验证 findBySlug 被调用两次（初始查询 + 冲突后重试）
            Mockito.verify(mockRepository, Mockito.times(2))
                .findBySlug(slug);
            
        } catch (IllegalArgumentException e) {
            // 允许合理的异常
            assertTrue(tagName.trim().isEmpty() || tagName.length() > 50,
                "只有空名称或超长名称才应该抛出异常");
        }
    }
}
