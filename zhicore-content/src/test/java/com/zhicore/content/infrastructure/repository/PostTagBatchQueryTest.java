package com.zhicore.content.infrastructure.repository;

import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.domain.service.TagDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Post-Tag 批量查询测试
 * 
 * 测试批量查询优化功能，验证避免 N+1 查询
 *
 * @author ZhiCore Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostTagBatchQueryTest {

    @Autowired
    private PostTagRepository postTagRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private TagDomainService tagDomainService;

    private Long postId1;
    private Long postId2;
    private Long postId3;
    private Tag tag1;
    private Tag tag2;
    private Tag tag3;

    @BeforeEach
    void setUp() {
        // 创建测试数据
        postId1 = 1001L;
        postId2 = 1002L;
        postId3 = 1003L;

        // 创建标签
        tag1 = tagDomainService.findOrCreate("Java");
        tag2 = tagDomainService.findOrCreate("Spring Boot");
        tag3 = tagDomainService.findOrCreate("PostgreSQL");

        // 建立关联
        // Post 1: Java, Spring Boot
        postTagRepository.attachBatch(postId1, Arrays.asList(tag1.getId(), tag2.getId()));
        
        // Post 2: Spring Boot, PostgreSQL
        postTagRepository.attachBatch(postId2, Arrays.asList(tag2.getId(), tag3.getId()));
        
        // Post 3: Java, PostgreSQL
        postTagRepository.attachBatch(postId3, Arrays.asList(tag1.getId(), tag3.getId()));
    }

    @Test
    void testFindTagIdsByPostIds() {
        // Given: 多个文章 ID
        List<Long> postIds = Arrays.asList(postId1, postId2, postId3);

        // When: 批量查询标签 ID
        Map<Long, List<Long>> result = postTagRepository.findTagIdsByPostIds(postIds);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(3, result.size());

        // 验证 Post 1 的标签
        List<Long> post1Tags = result.get(postId1);
        assertNotNull(post1Tags);
        assertEquals(2, post1Tags.size());
        assertTrue(post1Tags.contains(tag1.getId()));
        assertTrue(post1Tags.contains(tag2.getId()));

        // 验证 Post 2 的标签
        List<Long> post2Tags = result.get(postId2);
        assertNotNull(post2Tags);
        assertEquals(2, post2Tags.size());
        assertTrue(post2Tags.contains(tag2.getId()));
        assertTrue(post2Tags.contains(tag3.getId()));

        // 验证 Post 3 的标签
        List<Long> post3Tags = result.get(postId3);
        assertNotNull(post3Tags);
        assertEquals(2, post3Tags.size());
        assertTrue(post3Tags.contains(tag1.getId()));
        assertTrue(post3Tags.contains(tag3.getId()));
    }

    @Test
    void testFindTagsByPostIds() {
        // Given: 多个文章 ID
        List<Long> postIds = Arrays.asList(postId1, postId2, postId3);

        // When: 批量查询标签（包含完整 Tag 对象）
        Map<Long, List<Tag>> result = tagRepository.findTagsByPostIds(postIds);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(3, result.size());

        // 验证 Post 1 的标签
        List<Tag> post1Tags = result.get(postId1);
        assertNotNull(post1Tags);
        assertEquals(2, post1Tags.size());
        assertTrue(post1Tags.stream().anyMatch(t -> t.getSlug().equals("java")));
        assertTrue(post1Tags.stream().anyMatch(t -> t.getSlug().equals("spring-boot")));

        // 验证 Post 2 的标签
        List<Tag> post2Tags = result.get(postId2);
        assertNotNull(post2Tags);
        assertEquals(2, post2Tags.size());
        assertTrue(post2Tags.stream().anyMatch(t -> t.getSlug().equals("spring-boot")));
        assertTrue(post2Tags.stream().anyMatch(t -> t.getSlug().equals("postgresql")));

        // 验证 Post 3 的标签
        List<Tag> post3Tags = result.get(postId3);
        assertNotNull(post3Tags);
        assertEquals(2, post3Tags.size());
        assertTrue(post3Tags.stream().anyMatch(t -> t.getSlug().equals("java")));
        assertTrue(post3Tags.stream().anyMatch(t -> t.getSlug().equals("postgresql")));
    }

    @Test
    void testFindTagIdsByPostIds_EmptyList() {
        // Given: 空列表
        List<Long> postIds = List.of();

        // When: 批量查询
        Map<Long, List<Long>> result = postTagRepository.findTagIdsByPostIds(postIds);

        // Then: 返回空 Map
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindTagsByPostIds_EmptyList() {
        // Given: 空列表
        List<Long> postIds = List.of();

        // When: 批量查询
        Map<Long, List<Tag>> result = tagRepository.findTagsByPostIds(postIds);

        // Then: 返回空 Map
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindTagIdsByPostIds_NonExistentPost() {
        // Given: 不存在的文章 ID
        List<Long> postIds = Arrays.asList(9999L, 9998L);

        // When: 批量查询
        Map<Long, List<Long>> result = postTagRepository.findTagIdsByPostIds(postIds);

        // Then: 返回空 Map（没有关联）
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindTagsByPostIds_NonExistentPost() {
        // Given: 不存在的文章 ID
        List<Long> postIds = Arrays.asList(9999L, 9998L);

        // When: 批量查询
        Map<Long, List<Tag>> result = tagRepository.findTagsByPostIds(postIds);

        // Then: 返回空 Map（没有关联）
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
