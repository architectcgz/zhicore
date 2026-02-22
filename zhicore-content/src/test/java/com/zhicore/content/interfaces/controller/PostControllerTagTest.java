package com.zhicore.content.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.service.PostApplicationService;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.PostRepository;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.domain.service.TagDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostController 标签相关 API 测试
 * 
 * Feature: post-tag-need
 * 
 * @author ZhiCore Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostControllerTagTest {

    @Autowired
    private PostApplicationService postApplicationService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private PostTagRepository postTagRepository;

    @Autowired
    private TagDomainService tagDomainService;

    private Long testUserId;
    private Long testPostId;

    @BeforeEach
    void setUp() {
        testUserId = 1001L;
        testPostId = 2001L;

        // 设置用户上下文
        UserContext.UserInfo userInfo = new UserContext.UserInfo();
        userInfo.setUserId(String.valueOf(testUserId));
        userInfo.setUserName("testuser");
        UserContext.setUser(userInfo);

        // 创建测试文章
        Post post = Post.createDraft(testPostId, testUserId, "Test Post for Tags");
        postRepository.save(post);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * 测试为文章添加标签
     * 
     * Feature: post-tag-need
     * Task: 10.3 实现 POST /api/v1/posts/{id}/tags
     */
    @Test
    void testAttachTags() {
        // Given: 标签列表
        List<String> tagNames = Arrays.asList("Java", "Spring Boot", "后端开发");

        // When: 添加标签
        postApplicationService.replacePostTags(testUserId, testPostId, tagNames);

        // Then: 验证标签已添加
        List<Long> tagIds = postTagRepository.findTagIdsByPostId(testPostId);
        assertEquals(3, tagIds.size());

        // 验证标签内容
        List<Tag> tags = tagRepository.findByIdIn(tagIds);
        assertEquals(3, tags.size());
        assertTrue(tags.stream().anyMatch(tag -> tag.getName().equals("Java")));
        assertTrue(tags.stream().anyMatch(tag -> tag.getName().equals("Spring Boot")));
        assertTrue(tags.stream().anyMatch(tag -> tag.getName().equals("后端开发")));
    }

    /**
     * 测试获取文章的标签列表
     * 
     * Feature: post-tag-need
     * Task: 10.5 实现 GET /api/v1/posts/{id}/tags
     */
    @Test
    void testGetPostTags() {
        // Given: 文章已有标签
        List<String> tagNames = Arrays.asList("Java", "Spring Boot");
        postApplicationService.replacePostTags(testUserId, testPostId, tagNames);

        // When: 获取标签列表
        List<TagDTO> tags = postApplicationService.getPostTags(testPostId);

        // Then: 验证标签列表
        assertNotNull(tags);
        assertEquals(2, tags.size());
        assertTrue(tags.stream().anyMatch(tag -> tag.getName().equals("Java")));
        assertTrue(tags.stream().anyMatch(tag -> tag.getName().equals("Spring Boot")));
    }

    /**
     * 测试获取没有标签的文章
     * 
     * Feature: post-tag-need
     * Task: 10.5 实现 GET /api/v1/posts/{id}/tags
     */
    @Test
    void testGetPostTagsWhenNoTags() {
        // When: 获取没有标签的文章
        List<TagDTO> tags = postApplicationService.getPostTags(testPostId);

        // Then: 返回空列表
        assertNotNull(tags);
        assertTrue(tags.isEmpty());
    }

    /**
     * 测试移除文章的标签（通过替换实现）
     * 
     * Feature: post-tag-need
     * Task: 10.4 实现 DELETE /api/v1/posts/{id}/tags/{slug}
     */
    @Test
    void testDetachTag() {
        // Given: 文章已有标签
        List<String> tagNames = Arrays.asList("Java", "Spring Boot", "后端开发");
        postApplicationService.replacePostTags(testUserId, testPostId, tagNames);

        // When: 移除一个标签（通过获取当前标签并过滤）
        List<TagDTO> currentTags = postApplicationService.getPostTags(testPostId);
        List<String> remainingTagNames = currentTags.stream()
                .filter(tag -> !tag.getSlug().equals("spring-boot"))
                .map(TagDTO::getName)
                .toList();
        postApplicationService.replacePostTags(testUserId, testPostId, remainingTagNames);

        // Then: 验证标签已移除
        List<TagDTO> tags = postApplicationService.getPostTags(testPostId);
        assertEquals(2, tags.size());
        assertFalse(tags.stream().anyMatch(tag -> tag.getSlug().equals("spring-boot")));
        assertTrue(tags.stream().anyMatch(tag -> tag.getName().equals("Java")));
        assertTrue(tags.stream().anyMatch(tag -> tag.getName().equals("后端开发")));
    }

    /**
     * 测试替换文章标签
     * 
     * Feature: post-tag-need
     * Task: 10.3 实现 POST /api/v1/posts/{id}/tags
     */
    @Test
    void testReplacePostTags() {
        // Given: 文章已有标签
        List<String> oldTagNames = Arrays.asList("Java", "Spring Boot");
        postApplicationService.replacePostTags(testUserId, testPostId, oldTagNames);

        // When: 替换为新标签
        List<String> newTagNames = Arrays.asList("Python", "Django", "Web开发");
        postApplicationService.replacePostTags(testUserId, testPostId, newTagNames);

        // Then: 验证标签已替换
        List<TagDTO> tags = postApplicationService.getPostTags(testPostId);
        assertEquals(3, tags.size());
        assertFalse(tags.stream().anyMatch(tag -> tag.getName().equals("Java")));
        assertFalse(tags.stream().anyMatch(tag -> tag.getName().equals("Spring Boot")));
        assertTrue(tags.stream().anyMatch(tag -> tag.getName().equals("Python")));
        assertTrue(tags.stream().anyMatch(tag -> tag.getName().equals("Django")));
        assertTrue(tags.stream().anyMatch(tag -> tag.getName().equals("Web开发")));
    }

    /**
     * 测试添加空标签列表
     * 
     * Feature: post-tag-need
     * Task: 10.3 实现 POST /api/v1/posts/{id}/tags
     */
    @Test
    void testAttachEmptyTags() {
        // Given: 文章已有标签
        List<String> tagNames = Arrays.asList("Java", "Spring Boot");
        postApplicationService.replacePostTags(testUserId, testPostId, tagNames);

        // When: 替换为空列表
        postApplicationService.replacePostTags(testUserId, testPostId, Arrays.asList());

        // Then: 验证所有标签已移除
        List<TagDTO> tags = postApplicationService.getPostTags(testPostId);
        assertTrue(tags.isEmpty());
    }
}
