package com.zhicore.content.infrastructure.repository;

import com.zhicore.content.domain.repository.PostTagRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostTagRepository 集成测试
 * 
 * 测试 PostTagRepository 的数据库操作，包括关联创建、删除、查询等功能
 * 
 * 包含属性测试：
 * - Property 7: Post-Tag 关联唯一性
 * 
 * Validates: Requirements 4.2.4, 5.2.2
 *
 * @author ZhiCore Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("PostTagRepository 集成测试")
class PostTagRepositoryIntegrationTest {

    @Autowired
    private PostTagRepository postTagRepository;

    @BeforeEach
    void setUp() {
        // 每个测试前清理数据（由于使用 @Transactional，会自动回滚）
    }

    // ==================== 基本功能测试 ====================

    @Test
    @DisplayName("创建 Post-Tag 关联")
    void testAttach() {
        // Given: Post ID 和 Tag ID
        Long postId = 1001L;
        Long tagId = 2001L;

        // When: 创建关联
        postTagRepository.attach(postId, tagId);

        // Then: 关联应该存在
        assertThat(postTagRepository.exists(postId, tagId)).isTrue();
    }

    @Test
    @DisplayName("重复创建关联应该是幂等的")
    void testAttach_idempotent() {
        // Given: Post ID 和 Tag ID
        Long postId = 1002L;
        Long tagId = 2002L;

        // When: 多次创建相同的关联
        postTagRepository.attach(postId, tagId);
        postTagRepository.attach(postId, tagId);
        postTagRepository.attach(postId, tagId);

        // Then: 关联应该只存在一次
        assertThat(postTagRepository.exists(postId, tagId)).isTrue();
        assertThat(postTagRepository.countTagsByPostId(postId)).isEqualTo(1);
    }

    @Test
    @DisplayName("批量创建关联")
    void testAttachBatch() {
        // Given: Post ID 和多个 Tag ID
        Long postId = 1003L;
        List<Long> tagIds = Arrays.asList(2003L, 2004L, 2005L);

        // When: 批量创建关联
        postTagRepository.attachBatch(postId, tagIds);

        // Then: 所有关联应该存在
        assertThat(postTagRepository.exists(postId, 2003L)).isTrue();
        assertThat(postTagRepository.exists(postId, 2004L)).isTrue();
        assertThat(postTagRepository.exists(postId, 2005L)).isTrue();
        assertThat(postTagRepository.countTagsByPostId(postId)).isEqualTo(3);
    }

    @Test
    @DisplayName("批量创建关联 - 空列表")
    void testAttachBatch_emptyList() {
        // Given: Post ID 和空的 Tag ID 列表
        Long postId = 1004L;
        List<Long> tagIds = List.of();

        // When: 批量创建关联
        postTagRepository.attachBatch(postId, tagIds);

        // Then: 不应该有任何关联
        assertThat(postTagRepository.countTagsByPostId(postId)).isEqualTo(0);
    }

    @Test
    @DisplayName("批量创建关联 - 部分已存在")
    void testAttachBatch_partialExists() {
        // Given: Post ID 和多个 Tag ID，其中一些已存在
        Long postId = 1005L;
        postTagRepository.attach(postId, 2006L);
        postTagRepository.attach(postId, 2007L);

        List<Long> tagIds = Arrays.asList(2006L, 2007L, 2008L, 2009L);

        // When: 批量创建关联
        postTagRepository.attachBatch(postId, tagIds);

        // Then: 所有关联应该存在，但不重复
        assertThat(postTagRepository.countTagsByPostId(postId)).isEqualTo(4);
        assertThat(postTagRepository.exists(postId, 2006L)).isTrue();
        assertThat(postTagRepository.exists(postId, 2007L)).isTrue();
        assertThat(postTagRepository.exists(postId, 2008L)).isTrue();
        assertThat(postTagRepository.exists(postId, 2009L)).isTrue();
    }

    @Test
    @DisplayName("删除关联")
    void testDetach() {
        // Given: 已存在的关联
        Long postId = 1006L;
        Long tagId = 2010L;
        postTagRepository.attach(postId, tagId);

        // When: 删除关联
        postTagRepository.detach(postId, tagId);

        // Then: 关联应该不存在
        assertThat(postTagRepository.exists(postId, tagId)).isFalse();
    }

    @Test
    @DisplayName("删除不存在的关联")
    void testDetach_notExists() {
        // Given: 不存在的关联
        Long postId = 1007L;
        Long tagId = 2011L;

        // When: 删除关联
        postTagRepository.detach(postId, tagId);

        // Then: 不应该抛出异常
        assertThat(postTagRepository.exists(postId, tagId)).isFalse();
    }

    @Test
    @DisplayName("删除文章的所有关联")
    void testDetachAllByPostId() {
        // Given: 文章有多个标签
        Long postId = 1008L;
        postTagRepository.attach(postId, 2012L);
        postTagRepository.attach(postId, 2013L);
        postTagRepository.attach(postId, 2014L);

        // When: 删除所有关联
        postTagRepository.detachAllByPostId(postId);

        // Then: 所有关联应该不存在
        assertThat(postTagRepository.countTagsByPostId(postId)).isEqualTo(0);
        assertThat(postTagRepository.exists(postId, 2012L)).isFalse();
        assertThat(postTagRepository.exists(postId, 2013L)).isFalse();
        assertThat(postTagRepository.exists(postId, 2014L)).isFalse();
    }

    @Test
    @DisplayName("查询文章的所有标签ID")
    void testFindTagIdsByPostId() {
        // Given: 文章有多个标签
        Long postId = 1009L;
        postTagRepository.attach(postId, 2015L);
        postTagRepository.attach(postId, 2016L);
        postTagRepository.attach(postId, 2017L);

        // When: 查询标签ID列表
        List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);

        // Then: 应该返回所有标签ID
        assertThat(tagIds).hasSize(3);
        assertThat(tagIds).containsExactlyInAnyOrder(2015L, 2016L, 2017L);
    }

    @Test
    @DisplayName("查询文章的标签ID - 无标签")
    void testFindTagIdsByPostId_noTags() {
        // Given: 文章没有标签
        Long postId = 1010L;

        // When: 查询标签ID列表
        List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);

        // Then: 应该返回空列表
        assertThat(tagIds).isEmpty();
    }

    @Test
    @DisplayName("查询标签下的所有文章ID")
    void testFindPostIdsByTagId() {
        // Given: 标签关联多篇文章
        Long tagId = 2018L;
        postTagRepository.attach(1011L, tagId);
        postTagRepository.attach(1012L, tagId);
        postTagRepository.attach(1013L, tagId);

        // When: 查询文章ID列表
        List<Long> postIds = postTagRepository.findPostIdsByTagId(tagId);

        // Then: 应该返回所有文章ID
        assertThat(postIds).hasSize(3);
        assertThat(postIds).containsExactlyInAnyOrder(1011L, 1012L, 1013L);
    }

    @Test
    @DisplayName("查询标签下的文章ID - 无文章")
    void testFindPostIdsByTagId_noPosts() {
        // Given: 标签没有关联文章
        Long tagId = 2019L;

        // When: 查询文章ID列表
        List<Long> postIds = postTagRepository.findPostIdsByTagId(tagId);

        // Then: 应该返回空列表
        assertThat(postIds).isEmpty();
    }

    @Test
    @DisplayName("分页查询标签下的文章ID")
    void testFindPostIdsByTagId_pageable() {
        // Given: 标签关联多篇文章
        Long tagId = 2020L;
        for (long i = 1014L; i <= 1023L; i++) {
            postTagRepository.attach(i, tagId);
        }

        // When: 分页查询（第一页，每页5条）
        Pageable pageable = PageRequest.of(0, 5);
        Page<Long> page = postTagRepository.findPostIdsByTagId(tagId, pageable);

        // Then: 应该返回分页结果
        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getTotalElements()).isEqualTo(10);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getNumber()).isEqualTo(0);
    }

    @Test
    @DisplayName("分页查询标签下的文章ID - 第二页")
    void testFindPostIdsByTagId_pageable_secondPage() {
        // Given: 标签关联多篇文章
        Long tagId = 2021L;
        for (long i = 1024L; i <= 1033L; i++) {
            postTagRepository.attach(i, tagId);
        }

        // When: 分页查询（第二页，每页5条）
        Pageable pageable = PageRequest.of(1, 5);
        Page<Long> page = postTagRepository.findPostIdsByTagId(tagId, pageable);

        // Then: 应该返回第二页结果
        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getTotalElements()).isEqualTo(10);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("检查关联是否存在")
    void testExists() {
        // Given: 创建关联
        Long postId = 1034L;
        Long tagId = 2022L;
        postTagRepository.attach(postId, tagId);

        // When & Then: 检查存在的关联
        assertThat(postTagRepository.exists(postId, tagId)).isTrue();

        // When & Then: 检查不存在的关联
        assertThat(postTagRepository.exists(postId, 9999L)).isFalse();
        assertThat(postTagRepository.exists(9999L, tagId)).isFalse();
    }

    @Test
    @DisplayName("统计标签下的文章数量")
    void testCountPostsByTagId() {
        // Given: 标签关联多篇文章
        Long tagId = 2023L;
        postTagRepository.attach(1035L, tagId);
        postTagRepository.attach(1036L, tagId);
        postTagRepository.attach(1037L, tagId);

        // When: 统计文章数量
        int count = postTagRepository.countPostsByTagId(tagId);

        // Then: 应该返回正确的数量
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("统计标签下的文章数量 - 无文章")
    void testCountPostsByTagId_noPosts() {
        // Given: 标签没有关联文章
        Long tagId = 2024L;

        // When: 统计文章数量
        int count = postTagRepository.countPostsByTagId(tagId);

        // Then: 应该返回0
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("统计文章的标签数量")
    void testCountTagsByPostId() {
        // Given: 文章有多个标签
        Long postId = 1038L;
        postTagRepository.attach(postId, 2025L);
        postTagRepository.attach(postId, 2026L);
        postTagRepository.attach(postId, 2027L);
        postTagRepository.attach(postId, 2028L);

        // When: 统计标签数量
        int count = postTagRepository.countTagsByPostId(postId);

        // Then: 应该返回正确的数量
        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("统计文章的标签数量 - 无标签")
    void testCountTagsByPostId_noTags() {
        // Given: 文章没有标签
        Long postId = 1039L;

        // When: 统计标签数量
        int count = postTagRepository.countTagsByPostId(postId);

        // Then: 应该返回0
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("多个文章关联同一个标签")
    void testMultiplePostsSameTag() {
        // Given: 多个文章关联同一个标签
        Long tagId = 2029L;
        postTagRepository.attach(1040L, tagId);
        postTagRepository.attach(1041L, tagId);
        postTagRepository.attach(1042L, tagId);

        // When: 查询标签下的文章
        List<Long> postIds = postTagRepository.findPostIdsByTagId(tagId);

        // Then: 应该返回所有文章
        assertThat(postIds).hasSize(3);
        assertThat(postIds).containsExactlyInAnyOrder(1040L, 1041L, 1042L);
    }

    @Test
    @DisplayName("一个文章关联多个标签")
    void testSinglePostMultipleTags() {
        // Given: 一个文章关联多个标签
        Long postId = 1043L;
        postTagRepository.attach(postId, 2030L);
        postTagRepository.attach(postId, 2031L);
        postTagRepository.attach(postId, 2032L);

        // When: 查询文章的标签
        List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);

        // Then: 应该返回所有标签
        assertThat(tagIds).hasSize(3);
        assertThat(tagIds).containsExactlyInAnyOrder(2030L, 2031L, 2032L);
    }

    @Test
    @DisplayName("删除部分关联后查询")
    void testDetachAndQuery() {
        // Given: 文章有多个标签
        Long postId = 1044L;
        postTagRepository.attach(postId, 2033L);
        postTagRepository.attach(postId, 2034L);
        postTagRepository.attach(postId, 2035L);

        // When: 删除一个关联
        postTagRepository.detach(postId, 2034L);

        // Then: 应该只剩下两个标签
        List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);
        assertThat(tagIds).hasSize(2);
        assertThat(tagIds).containsExactlyInAnyOrder(2033L, 2035L);
        assertThat(postTagRepository.exists(postId, 2034L)).isFalse();
    }

    // ==================== 属性测试 ====================

    /**
     * Property 7: Post-Tag 关联唯一性
     * 
     * 对于任意 Post 和 Tag，多次建立关联应该是幂等的，不会创建重复记录
     * 
     * Validates: Requirements 4.2.4, 5.2.2
     * 
     * Feature: post-tag-need, Property 7: Post-Tag 关联唯一性
     */
    @Property(tries = 100)
    @DisplayName("Property 7: Post-Tag 关联唯一性")
    void property_postTagAssociationUniqueness(
            @ForAll @LongRange(min = 1L, max = 999999L) long postId,
            @ForAll @LongRange(min = 1L, max = 999999L) long tagId,
            @ForAll @LongRange(min = 1, max = 5) int attachTimes
    ) {
        // Given: Post ID 和 Tag ID
        
        // When: 多次创建相同的关联
        for (int i = 0; i < attachTimes; i++) {
            postTagRepository.attach(postId, tagId);
        }

        // Then: 关联应该只存在一次
        assertThat(postTagRepository.exists(postId, tagId)).isTrue();
        
        // 验证通过查询确认只有一条记录
        List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);
        long count = tagIds.stream().filter(id -> id.equals(tagId)).count();
        assertThat(count).isEqualTo(1);
        
        // 验证统计数量正确
        int tagCount = postTagRepository.countTagsByPostId(postId);
        assertThat(tagCount).isEqualTo(1);
        
        // 验证反向查询也正确
        List<Long> postIds = postTagRepository.findPostIdsByTagId(tagId);
        long postCount = postIds.stream().filter(id -> id.equals(postId)).count();
        assertThat(postCount).isEqualTo(1);
        
        // 再次调用 attach 应该仍然是幂等的
        postTagRepository.attach(postId, tagId);
        assertThat(postTagRepository.countTagsByPostId(postId)).isEqualTo(1);
    }

    /**
     * Property 7 扩展: 批量创建关联的唯一性
     * 
     * 对于任意 Post 和 Tag 列表，多次批量创建关联应该是幂等的
     * 
     * Validates: Requirements 4.2.4, 5.2.2
     * 
     * Feature: post-tag-need, Property 7: Post-Tag 关联唯一性（批量操作）
     */
    @Property(tries = 100)
    @DisplayName("Property 7 扩展: 批量创建关联的唯一性")
    void property_postTagBatchAssociationUniqueness(
            @ForAll @LongRange(min = 1L, max = 999999L) long postId,
            @ForAll("tagIdLists") List<Long> tagIds,
            @ForAll @LongRange(min = 1, max = 3) int batchTimes
    ) {
        // Given: Post ID 和 Tag ID 列表
        
        // When: 多次批量创建相同的关联
        for (int i = 0; i < batchTimes; i++) {
            postTagRepository.attachBatch(postId, tagIds);
        }

        // Then: 每个关联应该只存在一次
        int actualCount = postTagRepository.countTagsByPostId(postId);
        assertThat(actualCount).isEqualTo(tagIds.size());
        
        // 验证每个标签都只关联一次
        List<Long> foundTagIds = postTagRepository.findTagIdsByPostId(postId);
        assertThat(foundTagIds).hasSize(tagIds.size());
        assertThat(foundTagIds).containsExactlyInAnyOrderElementsOf(tagIds);
        
        // 验证每个标签的关联都存在
        for (Long tagId : tagIds) {
            assertThat(postTagRepository.exists(postId, tagId)).isTrue();
        }
    }

    // ==================== 数据生成器 ====================

    /**
     * 生成 Tag ID 列表
     * 
     * 规则：
     * - 列表大小 1-5
     * - ID 范围 1-999999
     * - 不包含重复 ID
     */
    @Provide
    Arbitrary<List<Long>> tagIdLists() {
        return Arbitraries.longs()
                .between(1L, 999999L)
                .list()
                .ofMinSize(1)
                .ofMaxSize(5)
                .map(list -> list.stream().distinct().toList());
    }
}
