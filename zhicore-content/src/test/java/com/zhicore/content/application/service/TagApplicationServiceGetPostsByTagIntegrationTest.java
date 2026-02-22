package com.zhicore.content.application.service;

import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.PostRepository;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.domain.service.TagDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TagApplicationService.getPostsByTag 集成测试
 * 
 * 测试按 Tag 查询 Post 的功能，包括：
 * - Property 9: 按 Tag 查询 Post 的正确性
 * - Property 10: 分页查询一致性
 * - Property 11: 分页查询完整性
 * 
 * Feature: post-tag-need
 * Validates: Requirements 4.3.1, 4.3.2
 * 
 * @author ZhiCore Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TagApplicationServiceGetPostsByTagIntegrationTest {

    @Autowired
    private TagApplicationService tagApplicationService;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostTagRepository postTagRepository;

    @Autowired
    private TagDomainService tagDomainService;

    private Tag testTag;
    private List<Post> testPosts;

    @BeforeEach
    void setUp() {
        // 创建测试标签
        testTag = tagDomainService.findOrCreate("Integration Test Tag");
        
        // 创建测试文章列表
        testPosts = new ArrayList<>();
    }

    // ==================== 正常功能测试 ====================

    /**
     * 测试 getPostsByTag - 正常查询
     * 
     * Validates: Requirements 4.3.1
     */
    @Test
    void getPostsByTagShouldReturnPostsWithTag() {
        // Given: 创建 5 篇文章并关联到标签
        for (int i = 0; i < 5; i++) {
            Post post = createAndSavePost("Test Post " + i, 1000L);
            testPosts.add(post);
            postTagRepository.attach(post.getId(), testTag.getId());
        }

        // When: 查询标签下的文章
        PageResult<PostVO> result = tagApplicationService.getPostsByTag(testTag.getSlug(), 0, 20);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(5, result.getTotal());
        assertEquals(5, result.getRecords().size());
        
        // 验证所有返回的文章都在测试文章列表中
        Set<Long> expectedPostIds = testPosts.stream()
                .map(Post::getId)
                .collect(Collectors.toSet());
        
        for (PostVO postVO : result.getRecords()) {
            assertTrue(expectedPostIds.contains(postVO.getId()), 
                    "返回的文章应该在测试文章列表中");
        }
    }

    /**
     * 测试 getPostsByTag - 标签不存在应抛出异常
     * 
     * Validates: Requirements 4.3.1
     */
    @Test
    void getPostsByTagWithNonExistentTagShouldThrowException() {
        // Given: 不存在的标签 slug
        String nonExistentSlug = "non-existent-tag-" + System.currentTimeMillis();

        // When & Then: 应抛出 ResourceNotFoundException
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            tagApplicationService.getPostsByTag(nonExistentSlug, 0, 20);
        });

        assertTrue(exception.getMessage().contains("标签不存在"));
        assertTrue(exception.getMessage().contains(nonExistentSlug));
    }

    /**
     * 测试 getPostsByTag - 标签下无文章应返回空列表
     * 
     * Validates: Requirements 4.3.1
     */
    @Test
    void getPostsByTagWithNoPostsShouldReturnEmptyList() {
        // Given: 创建一个没有关联文章的标签
        Tag emptyTag = tagDomainService.findOrCreate("Empty Tag " + System.currentTimeMillis());

        // When: 查询标签下的文章
        PageResult<PostVO> result = tagApplicationService.getPostsByTag(emptyTag.getSlug(), 0, 20);

        // Then: 应返回空列表
        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    /**
     * 测试 getPostsByTag - 分页查询第一页
     * 
     * Validates: Requirements 4.3.2
     */
    @Test
    void getPostsByTagFirstPageShouldReturnCorrectPage() {
        // Given: 创建 25 篇文章并关联到标签
        for (int i = 0; i < 25; i++) {
            Post post = createAndSavePost("Test Post " + i, 1000L);
            testPosts.add(post);
            postTagRepository.attach(post.getId(), testTag.getId());
        }

        // When: 查询第一页（每页 10 条）
        PageResult<PostVO> result = tagApplicationService.getPostsByTag(testTag.getSlug(), 0, 10);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(0, result.getCurrent());
        assertEquals(10, result.getSize());
        assertEquals(25, result.getTotal());
        assertEquals(10, result.getRecords().size());
    }

    /**
     * 测试 getPostsByTag - 分页查询第二页
     * 
     * Validates: Requirements 4.3.2
     */
    @Test
    void getPostsByTagSecondPageShouldReturnCorrectPage() {
        // Given: 创建 25 篇文章并关联到标签
        for (int i = 0; i < 25; i++) {
            Post post = createAndSavePost("Test Post " + i, 1000L);
            testPosts.add(post);
            postTagRepository.attach(post.getId(), testTag.getId());
        }

        // When: 查询第二页（每页 10 条）
        PageResult<PostVO> result = tagApplicationService.getPostsByTag(testTag.getSlug(), 1, 10);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(1, result.getCurrent());
        assertEquals(10, result.getSize());
        assertEquals(25, result.getTotal());
        assertEquals(10, result.getRecords().size());
    }

    /**
     * 测试 getPostsByTag - 分页查询最后一页（不满页）
     * 
     * Validates: Requirements 4.3.2
     */
    @Test
    void getPostsByTagLastPageShouldReturnRemainingPosts() {
        // Given: 创建 25 篇文章并关联到标签
        for (int i = 0; i < 25; i++) {
            Post post = createAndSavePost("Test Post " + i, 1000L);
            testPosts.add(post);
            postTagRepository.attach(post.getId(), testTag.getId());
        }

        // When: 查询第三页（每页 10 条，最后一页只有 5 条）
        PageResult<PostVO> result = tagApplicationService.getPostsByTag(testTag.getSlug(), 2, 10);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(2, result.getCurrent());
        assertEquals(10, result.getSize());
        assertEquals(25, result.getTotal());
        assertEquals(5, result.getRecords().size());
    }

    /**
     * 测试 getPostsByTag - 查询超出范围的页码应返回空列表
     * 
     * Validates: Requirements 4.3.2
     */
    @Test
    void getPostsByTagWithPageOutOfRangeShouldReturnEmptyList() {
        // Given: 创建 5 篇文章并关联到标签
        for (int i = 0; i < 5; i++) {
            Post post = createAndSavePost("Test Post " + i, 1000L);
            testPosts.add(post);
            postTagRepository.attach(post.getId(), testTag.getId());
        }

        // When: 查询第 10 页（超出范围）
        PageResult<PostVO> result = tagApplicationService.getPostsByTag(testTag.getSlug(), 10, 10);

        // Then: 应返回空列表
        assertNotNull(result);
        assertEquals(10, result.getCurrent());
        assertEquals(10, result.getSize());
        assertEquals(5, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    // ==================== Property-Based Tests (Converted to Parameterized Tests) ====================

    /**
     * Property 9: 按 Tag 查询 Post 的正确性
     * 
     * 对于任意 Tag，查询该 Tag 下的所有 Post，返回的每个 Post 都应该关联了该 Tag
     * 
     * Feature: post-tag-need, Property 9: 按 Tag 查询 Post 的正确性
     * Validates: Requirements 4.3.1
     */
    @Test
    void property9_getPostsByTagShouldReturnOnlyPostsWithTag() {
        // Test with different post counts
        int[] postCounts = {1, 5, 10, 20};
        
        for (int postCount : postCounts) {
            // Given: 创建一个标签和指定数量的文章
            Tag tag = tagDomainService.findOrCreate("Property Test Tag " + System.currentTimeMillis());
            List<Post> posts = new ArrayList<>();
            
            for (int i = 0; i < postCount; i++) {
                Post post = createAndSavePost("Property Test Post " + i + " " + System.currentTimeMillis(), 1000L);
                posts.add(post);
                postTagRepository.attach(post.getId(), tag.getId());
            }
            
            // 创建一些不关联该标签的文章（干扰数据）
            for (int i = 0; i < 3; i++) {
                createAndSavePost("Unrelated Post " + i + " " + System.currentTimeMillis(), 2000L);
            }
            
            // When: 查询标签下的所有文章（使用大页面大小确保获取所有文章）
            PageResult<PostVO> result = tagApplicationService.getPostsByTag(tag.getSlug(), 0, 100);
            
            // Then: 验证所有返回的文章都关联了该标签
            assertNotNull(result);
            assertEquals(postCount, result.getTotal(), "返回的文章总数应该等于关联的文章数");
            assertEquals(postCount, result.getRecords().size(), "返回的文章列表大小应该等于关联的文章数");
            
            // 验证每个返回的文章都在关联列表中
            Set<Long> expectedPostIds = posts.stream()
                    .map(Post::getId)
                    .collect(Collectors.toSet());
            
            for (PostVO postVO : result.getRecords()) {
                assertTrue(expectedPostIds.contains(postVO.getId()), 
                        "返回的文章 " + postVO.getId() + " 应该关联了标签 " + tag.getSlug());
            }
            
            // 验证没有返回未关联的文章
            assertEquals(expectedPostIds.size(), result.getRecords().size(), 
                    "不应该返回未关联标签的文章");
        }
    }

    /**
     * Property 10: 分页查询一致性
     * 
     * 对于任意 Tag 和分页参数，在数据未发生变更，且分页查询使用稳定排序条件的前提下，
     * 多次查询同一页应该返回相同的结果
     * 
     * Feature: post-tag-need, Property 10: 分页查询一致性
     * Validates: Requirements 4.3.2
     */
    @Test
    void property10_paginationConsistency() {
        // Test with different combinations
        int[][] testCases = {
            {10, 0, 5},  // 10 posts, page 0, size 5
            {15, 1, 5},  // 15 posts, page 1, size 5
            {20, 0, 10}, // 20 posts, page 0, size 10
        };
        
        for (int[] testCase : testCases) {
            int postCount = testCase[0];
            int page = testCase[1];
            int size = testCase[2];
            
            // Given: 创建一个标签和指定数量的文章
            Tag tag = tagDomainService.findOrCreate("Consistency Test Tag " + System.currentTimeMillis());
            
            for (int i = 0; i < postCount; i++) {
                Post post = createAndSavePost("Consistency Test Post " + i + " " + System.currentTimeMillis(), 1000L);
                postTagRepository.attach(post.getId(), tag.getId());
            }
            
            // When: 多次查询同一页
            PageResult<PostVO> result1 = tagApplicationService.getPostsByTag(tag.getSlug(), page, size);
            PageResult<PostVO> result2 = tagApplicationService.getPostsByTag(tag.getSlug(), page, size);
            PageResult<PostVO> result3 = tagApplicationService.getPostsByTag(tag.getSlug(), page, size);
            
            // Then: 验证多次查询结果一致
            assertNotNull(result1);
            assertNotNull(result2);
            assertNotNull(result3);
            
            assertEquals(result1.getTotal(), result2.getTotal(), "总数应该一致");
            assertEquals(result1.getTotal(), result3.getTotal(), "总数应该一致");
            
            assertEquals(result1.getRecords().size(), result2.getRecords().size(), "返回的文章数量应该一致");
            assertEquals(result1.getRecords().size(), result3.getRecords().size(), "返回的文章数量应该一致");
            
            // 验证返回的文章 ID 列表一致
            List<Long> ids1 = result1.getRecords().stream().map(PostVO::getId).collect(Collectors.toList());
            List<Long> ids2 = result2.getRecords().stream().map(PostVO::getId).collect(Collectors.toList());
            List<Long> ids3 = result3.getRecords().stream().map(PostVO::getId).collect(Collectors.toList());
            
            assertEquals(ids1, ids2, "多次查询返回的文章 ID 列表应该一致");
            assertEquals(ids1, ids3, "多次查询返回的文章 ID 列表应该一致");
        }
    }

    /**
     * Property 11: 分页查询完整性
     * 
     * 对于任意 Tag，遍历所有分页结果，应该包含该 Tag 下的所有 Post，且不重复
     * 
     * Feature: post-tag-need, Property 11: 分页查询完整性
     * Validates: Requirements 4.3.2
     */
    @Test
    void property11_paginationCompleteness() {
        // Test with different combinations
        int[][] testCases = {
            {10, 5},  // 10 posts, page size 5
            {15, 7},  // 15 posts, page size 7
            {25, 10}, // 25 posts, page size 10
        };
        
        for (int[] testCase : testCases) {
            int postCount = testCase[0];
            int pageSize = testCase[1];
            
            // Given: 创建一个标签和指定数量的文章
            Tag tag = tagDomainService.findOrCreate("Completeness Test Tag " + System.currentTimeMillis());
            Set<Long> expectedPostIds = new HashSet<>();
            
            for (int i = 0; i < postCount; i++) {
                Post post = createAndSavePost("Completeness Test Post " + i + " " + System.currentTimeMillis(), 1000L);
                expectedPostIds.add(post.getId());
                postTagRepository.attach(post.getId(), tag.getId());
            }
            
            // When: 遍历所有分页
            Set<Long> actualPostIds = new HashSet<>();
            List<Long> allPostIds = new ArrayList<>();
            int totalPages = (postCount + pageSize - 1) / pageSize;
            
            for (int page = 0; page < totalPages; page++) {
                PageResult<PostVO> result = tagApplicationService.getPostsByTag(tag.getSlug(), page, pageSize);
                
                for (PostVO postVO : result.getRecords()) {
                    actualPostIds.add(postVO.getId());
                    allPostIds.add(postVO.getId());
                }
            }
            
            // Then: 验证完整性和无重复
            assertEquals(expectedPostIds.size(), actualPostIds.size(), 
                    "遍历所有分页应该包含所有文章");
            assertEquals(expectedPostIds, actualPostIds, 
                    "遍历所有分页应该包含所有文章，且不遗漏");
            
            // 验证无重复
            assertEquals(allPostIds.size(), actualPostIds.size(), 
                    "遍历所有分页不应该有重复的文章");
            
            // 验证总数正确
            PageResult<PostVO> firstPage = tagApplicationService.getPostsByTag(tag.getSlug(), 0, pageSize);
            assertEquals(postCount, firstPage.getTotal(), 
                    "第一页返回的总数应该等于实际文章数");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建并保存文章
     */
    private Post createAndSavePost(String title, Long ownerId) {
        Post post = Post.createDraft(
                generatePostId(),
                ownerId,
                title
        );
        
        // 设置为已发布状态
        post.publish();
        
        // 保存
        postRepository.save(post);
        
        // 返回文章对象
        return post;
    }

    /**
     * 生成文章 ID（简化版，实际应使用 Leaf）
     */
    private Long generatePostId() {
        return System.currentTimeMillis() + new Random().nextInt(10000);
    }
}
