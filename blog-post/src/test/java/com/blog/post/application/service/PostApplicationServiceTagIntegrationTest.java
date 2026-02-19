package com.blog.post.application.service;

import com.blog.api.client.IdGeneratorFeignClient;
import com.blog.common.exception.BusinessException;
import com.blog.common.exception.ForbiddenException;
import com.blog.common.result.ApiResponse;
import com.blog.post.domain.model.Post;
import com.blog.post.domain.model.Tag;
import com.blog.post.domain.repository.PostRepository;
import com.blog.post.domain.repository.PostTagRepository;
import com.blog.post.domain.repository.TagRepository;
import com.blog.post.domain.service.DualStorageManager;
import com.blog.post.domain.service.TagDomainService;
import com.blog.post.infrastructure.mq.PostEventPublisher;
import com.blog.post.infrastructure.mongodb.document.PostContent;
import com.blog.post.interfaces.dto.request.CreatePostRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PostApplicationService Tag 集成测试
 * 测试创建文章时附加标签的功能
 * 
 * 使用 Mockito 模拟外部依赖，专注于测试标签关联逻辑
 * 
 * Feature: post-tag-need
 * 
 * @author Blog Team
 */
@ExtendWith(MockitoExtension.class)
class PostApplicationServiceTagIntegrationTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostEventPublisher eventPublisher;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Mock
    private com.blog.post.infrastructure.feign.BlogUploadClient blogUploadClient;

    @Mock
    private DualStorageManager dualStorageManager;

    @Mock
    private com.blog.post.domain.service.DraftManager draftManager;

    @Mock
    private TagDomainService tagDomainService;

    @Mock
    private PostTagRepository postTagRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private PostApplicationService postApplicationService;

    private Long testUserId;
    private Long testPostId;

    @BeforeEach
    void setUp() {
        testUserId = 1000L;
        testPostId = 2000L;
        
        // Mock ID generation - lenient because not all tests create posts
        ApiResponse<Long> idResponse = ApiResponse.success(testPostId);
        lenient().when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(idResponse);
        
        // Mock dual storage manager - createPost returns Long
        // Use lenient() because some tests may not call this method (e.g., validation failures, update tests)
        lenient().when(dualStorageManager.createPost(any(Post.class), any(PostContent.class))).thenReturn(testPostId);
    }

    // ==================== Property 5: Post-Tag 关联创建 ====================

    /**
     * Property 5: Post-Tag 关联创建 - 创建文章时附加标签应成功关联
     * 
     * Feature: post-tag-need, Property 5: Post-Tag 关联创建
     * Validates: Requirements 4.2.1
     */
    @Test
    void createPostWithTagsShouldAssociateAllTags() {
        // Given: 3个标签
        List<String> tagNames = List.of("Java", "Spring", "DDD");
        List<Tag> mockTags = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        
        for (int i = 0; i < tagNames.size(); i++) {
            Long tagId = 3000L + i;
            String slug = tagNames.get(i).toLowerCase();
            Tag mockTag = Tag.create(tagId, tagNames.get(i), slug);
            mockTags.add(mockTag);
            tagIds.add(tagId);
        }
        
        // Mock TagDomainService behavior
        when(tagDomainService.findOrCreateBatch(tagNames)).thenReturn(mockTags);
        
        // Mock PostTagRepository behavior
        doNothing().when(postTagRepository).attachBatch(eq(testPostId), anyList());
        
        // When: 创建文章并附加标签
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Test Post");
        request.setContent("Test Content");
        request.setTags(tagNames);
        
        Long postId = postApplicationService.createPost(testUserId, request);
        
        // Then: 验证文章创建成功
        assertNotNull(postId);
        assertEquals(testPostId, postId);
        
        // 验证 TagDomainService 被调用
        verify(tagDomainService, times(1)).findOrCreateBatch(tagNames);
        
        // 验证 PostTagRepository.attachBatch 被调用
        verify(postTagRepository, times(1)).attachBatch(eq(testPostId), argThat(ids -> 
            ids != null && ids.size() == 3 && ids.containsAll(tagIds)
        ));
        
        // 验证 DualStorageManager 被调用
        verify(dualStorageManager, times(1)).createPost(any(Post.class), any(PostContent.class));
    }

    /**
     * Property 5 扩展: 测试空标签列表
     * 
     * Feature: post-tag-need, Property 5: Post-Tag 关联创建
     * Validates: Requirements 4.2.1
     */
    @Test
    void createPostWithEmptyTagsShouldSucceed() {
        // Given: 空标签列表
        List<String> emptyTags = new ArrayList<>();
        
        // When: 创建文章
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Test Post");
        request.setContent("Test Content");
        request.setTags(emptyTags);
        
        Long postId = postApplicationService.createPost(testUserId, request);
        
        // Then: 验证文章创建成功
        assertNotNull(postId);
        assertEquals(testPostId, postId);
        
        // 验证 TagDomainService 没有被调用（因为标签列表为空）
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        
        // 验证 PostTagRepository.attachBatch 没有被调用
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
        
        // 验证 DualStorageManager 被调用
        verify(dualStorageManager, times(1)).createPost(any(Post.class), any(PostContent.class));
    }

    /**
     * Property 5 扩展: 测试 null 标签列表
     * 
     * Feature: post-tag-need, Property 5: Post-Tag 关联创建
     * Validates: Requirements 4.2.1
     */
    @Test
    void createPostWithNullTagsShouldSucceed() {
        // Given: null 标签列表
        
        // When: 创建文章
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Test Post");
        request.setContent("Test Content");
        request.setTags(null);
        
        Long postId = postApplicationService.createPost(testUserId, request);
        
        // Then: 验证文章创建成功
        assertNotNull(postId);
        assertEquals(testPostId, postId);
        
        // 验证 TagDomainService 没有被调用
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        
        // 验证 PostTagRepository.attachBatch 没有被调用
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
    }

    /**
     * Property 5 扩展: 测试重复标签名称
     * 
     * 对于包含重复标签名称的列表，应该去重并只创建一次关联
     * 
     * Feature: post-tag-need, Property 5: Post-Tag 关联创建
     * Validates: Requirements 4.2.1
     */
    @Test
    void createPostWithDuplicateTagsShouldDeduplicate() {
        // Given: 包含重复标签的列表（不同大小写和空格）
        String baseTagName = "Java";
        List<String> tagNames = List.of(
            baseTagName,
            baseTagName.toUpperCase(),
            baseTagName.toLowerCase(),
            " " + baseTagName + " ",
            baseTagName + "  "
        );
        
        // Mock: TagDomainService 应该去重并只返回一个 Tag
        String slug = baseTagName.toLowerCase();
        Tag mockTag = Tag.create(3000L, baseTagName, slug);
        when(tagDomainService.findOrCreateBatch(tagNames)).thenReturn(List.of(mockTag));
        
        // When: 创建文章
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Test Post");
        request.setContent("Test Content");
        request.setTags(tagNames);
        
        Long postId = postApplicationService.createPost(testUserId, request);
        
        // Then: 验证文章创建成功
        assertNotNull(postId);
        
        // 验证 TagDomainService 被调用（它负责去重）
        verify(tagDomainService, times(1)).findOrCreateBatch(tagNames);
        
        // 验证 PostTagRepository.attachBatch 被调用，且只有一个标签ID
        verify(postTagRepository, times(1)).attachBatch(eq(testPostId), argThat(ids -> 
            ids != null && ids.size() == 1 && ids.contains(3000L)
        ));
    }

    // ==================== Property 8: Tag 数量上限 ====================

    /**
     * Property 8: Tag 数量上限 - 超过10个标签应该被拒绝
     * 
     * Feature: post-tag-need, Property 8: Tag 数量上限
     * Validates: Requirements 4.2.5
     */
    @Test
    void createPostWithMoreThan10TagsShouldBeRejected() {
        // Given: 生成11个标签
        List<String> tagNames = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            tagNames.add("tag" + i);
        }
        
        // When & Then: 创建文章应该抛出异常
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Test Post");
        request.setContent("Test Content");
        request.setTags(tagNames);
        
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            postApplicationService.createPost(testUserId, request);
        });
        
        // 验证异常消息
        assertTrue(exception.getMessage().contains("10") || 
                   exception.getMessage().contains("标签"),
            "异常消息应该提示标签数量限制: " + exception.getMessage());
        
        // 验证 TagDomainService 没有被调用（因为在验证阶段就失败了）
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        
        // 验证 PostTagRepository 没有被调用
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
        
        // 验证 DualStorageManager 没有被调用
        verify(dualStorageManager, never()).createPost(any(Post.class), any(PostContent.class));
    }

    /**
     * Property 8 扩展: 测试边界值（正好10个标签）
     * 
     * Feature: post-tag-need, Property 8: Tag 数量上限
     * Validates: Requirements 4.2.5
     */
    @Test
    void createPostWithExactly10TagsShouldSucceed() {
        // Given: 正好10个标签
        List<String> tagNames = new ArrayList<>();
        List<Tag> mockTags = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            String tagName = "tag" + i;
            tagNames.add(tagName);
            
            Long tagId = 3000L + i;
            String slug = tagName.toLowerCase();
            Tag mockTag = Tag.create(tagId, tagName, slug);
            mockTags.add(mockTag);
            tagIds.add(tagId);
        }
        
        // Mock TagDomainService behavior
        when(tagDomainService.findOrCreateBatch(tagNames)).thenReturn(mockTags);
        
        // Mock PostTagRepository behavior
        doNothing().when(postTagRepository).attachBatch(eq(testPostId), anyList());
        
        // When: 创建文章
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Test Post");
        request.setContent("Test Content");
        request.setTags(tagNames);
        
        Long postId = postApplicationService.createPost(testUserId, request);
        
        // Then: 验证文章创建成功
        assertNotNull(postId);
        assertEquals(testPostId, postId);
        
        // 验证 TagDomainService 被调用
        verify(tagDomainService, times(1)).findOrCreateBatch(tagNames);
        
        // 验证 PostTagRepository.attachBatch 被调用，且有10个标签
        verify(postTagRepository, times(1)).attachBatch(eq(testPostId), argThat(ids -> 
            ids != null && ids.size() == 10
        ));
    }

    /**
     * Property 8 扩展: 测试边界值（9个标签）
     * 
     * Feature: post-tag-need, Property 8: Tag 数量上限
     * Validates: Requirements 4.2.5
     */
    @Test
    void createPostWith9TagsShouldSucceed() {
        // Given: 9个标签
        List<String> tagNames = new ArrayList<>();
        List<Tag> mockTags = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        
        for (int i = 0; i < 9; i++) {
            String tagName = "tag" + i;
            tagNames.add(tagName);
            
            Long tagId = 3000L + i;
            String slug = tagName.toLowerCase();
            Tag mockTag = Tag.create(tagId, tagName, slug);
            mockTags.add(mockTag);
            tagIds.add(tagId);
        }
        
        // Mock TagDomainService behavior
        when(tagDomainService.findOrCreateBatch(tagNames)).thenReturn(mockTags);
        
        // Mock PostTagRepository behavior
        doNothing().when(postTagRepository).attachBatch(eq(testPostId), anyList());
        
        // When: 创建文章
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("Test Post");
        request.setContent("Test Content");
        request.setTags(tagNames);
        
        Long postId = postApplicationService.createPost(testUserId, request);
        
        // Then: 验证文章创建成功
        assertNotNull(postId);
        assertEquals(testPostId, postId);
        
        // 验证 TagDomainService 被调用
        verify(tagDomainService, times(1)).findOrCreateBatch(tagNames);
        
        // 验证 PostTagRepository.attachBatch 被调用，且有9个标签
        verify(postTagRepository, times(1)).attachBatch(eq(testPostId), argThat(ids -> 
            ids != null && ids.size() == 9
        ));
    }

    // ==================== 更新文章标签测试 ====================

    /**
     * 测试更新文章时添加标签
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void updatePostWithNewTagsShouldAddTags() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Original Title");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        // Mock: 新标签
        List<String> newTagNames = List.of("Java", "Spring", "DDD");
        List<Tag> mockTags = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        
        for (int i = 0; i < newTagNames.size(); i++) {
            Long tagId = 3000L + i;
            String slug = newTagNames.get(i).toLowerCase();
            Tag mockTag = Tag.create(tagId, newTagNames.get(i), slug);
            mockTags.add(mockTag);
            tagIds.add(tagId);
        }
        
        when(tagDomainService.findOrCreateBatch(newTagNames)).thenReturn(mockTags);
        doNothing().when(postTagRepository).detachAllByPostId(testPostId);
        doNothing().when(postTagRepository).attachBatch(eq(testPostId), anyList());
        doNothing().when(dualStorageManager).updatePost(any(Post.class), any(PostContent.class));
        
        // When: 更新文章并添加标签
        com.blog.post.interfaces.dto.request.UpdatePostRequest request = 
            new com.blog.post.interfaces.dto.request.UpdatePostRequest();
        request.setTitle("Updated Title");
        request.setContent("Updated Content");
        request.setTags(newTagNames);
        
        postApplicationService.updatePost(testUserId, testPostId, request);
        
        // Then: 验证标签操作
        verify(postTagRepository, times(1)).detachAllByPostId(testPostId);
        verify(tagDomainService, times(1)).findOrCreateBatch(newTagNames);
        verify(postTagRepository, times(1)).attachBatch(eq(testPostId), argThat(ids -> 
            ids != null && ids.size() == 3 && ids.containsAll(tagIds)
        ));
        verify(dualStorageManager, times(1)).updatePost(any(Post.class), any(PostContent.class));
    }

    /**
     * 测试更新文章时移除所有标签
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void updatePostWithEmptyTagsShouldRemoveAllTags() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Original Title");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        doNothing().when(postTagRepository).detachAllByPostId(testPostId);
        doNothing().when(dualStorageManager).updatePost(any(Post.class), any(PostContent.class));
        
        // When: 更新文章并清空标签
        com.blog.post.interfaces.dto.request.UpdatePostRequest request = 
            new com.blog.post.interfaces.dto.request.UpdatePostRequest();
        request.setTitle("Updated Title");
        request.setContent("Updated Content");
        request.setTags(new ArrayList<>()); // 空列表
        
        postApplicationService.updatePost(testUserId, testPostId, request);
        
        // Then: 验证删除了所有标签，但没有添加新标签
        verify(postTagRepository, times(1)).detachAllByPostId(testPostId);
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
        verify(dualStorageManager, times(1)).updatePost(any(Post.class), any(PostContent.class));
    }

    /**
     * 测试更新文章时不修改标签（tags 为 null）
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void updatePostWithNullTagsShouldNotModifyTags() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Original Title");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        doNothing().when(dualStorageManager).updatePost(any(Post.class), any(PostContent.class));
        
        // When: 更新文章但不提供标签字段（null）
        com.blog.post.interfaces.dto.request.UpdatePostRequest request = 
            new com.blog.post.interfaces.dto.request.UpdatePostRequest();
        request.setTitle("Updated Title");
        request.setContent("Updated Content");
        request.setTags(null); // null 表示不修改标签
        
        postApplicationService.updatePost(testUserId, testPostId, request);
        
        // Then: 验证没有进行任何标签操作
        verify(postTagRepository, never()).detachAllByPostId(anyLong());
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
        verify(dualStorageManager, times(1)).updatePost(any(Post.class), any(PostContent.class));
    }

    /**
     * 测试更新文章时替换标签
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void updatePostWithDifferentTagsShouldReplaceTags() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Original Title");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        // Mock: 新标签（与旧标签不同）
        List<String> newTagNames = List.of("Python", "Django", "REST");
        List<Tag> mockTags = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        
        for (int i = 0; i < newTagNames.size(); i++) {
            Long tagId = 4000L + i;
            String slug = newTagNames.get(i).toLowerCase();
            Tag mockTag = Tag.create(tagId, newTagNames.get(i), slug);
            mockTags.add(mockTag);
            tagIds.add(tagId);
        }
        
        when(tagDomainService.findOrCreateBatch(newTagNames)).thenReturn(mockTags);
        doNothing().when(postTagRepository).detachAllByPostId(testPostId);
        doNothing().when(postTagRepository).attachBatch(eq(testPostId), anyList());
        doNothing().when(dualStorageManager).updatePost(any(Post.class), any(PostContent.class));
        
        // When: 更新文章并替换标签
        com.blog.post.interfaces.dto.request.UpdatePostRequest request = 
            new com.blog.post.interfaces.dto.request.UpdatePostRequest();
        request.setTitle("Updated Title");
        request.setContent("Updated Content");
        request.setTags(newTagNames);
        
        postApplicationService.updatePost(testUserId, testPostId, request);
        
        // Then: 验证先删除旧标签，再添加新标签
        verify(postTagRepository, times(1)).detachAllByPostId(testPostId);
        verify(tagDomainService, times(1)).findOrCreateBatch(newTagNames);
        verify(postTagRepository, times(1)).attachBatch(eq(testPostId), argThat(ids -> 
            ids != null && ids.size() == 3 && ids.containsAll(tagIds)
        ));
    }

    /**
     * 测试更新文章时标签部分重复
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void updatePostWithPartiallyOverlappingTagsShouldReplaceAll() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Original Title");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        // Mock: 新标签（部分与旧标签重复）
        List<String> newTagNames = List.of("Java", "Python", "Go");
        List<Tag> mockTags = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        
        for (int i = 0; i < newTagNames.size(); i++) {
            Long tagId = 3000L + i;
            String slug = newTagNames.get(i).toLowerCase();
            Tag mockTag = Tag.create(tagId, newTagNames.get(i), slug);
            mockTags.add(mockTag);
            tagIds.add(tagId);
        }
        
        when(tagDomainService.findOrCreateBatch(newTagNames)).thenReturn(mockTags);
        doNothing().when(postTagRepository).detachAllByPostId(testPostId);
        doNothing().when(postTagRepository).attachBatch(eq(testPostId), anyList());
        doNothing().when(dualStorageManager).updatePost(any(Post.class), any(PostContent.class));
        
        // When: 更新文章
        com.blog.post.interfaces.dto.request.UpdatePostRequest request = 
            new com.blog.post.interfaces.dto.request.UpdatePostRequest();
        request.setTitle("Updated Title");
        request.setContent("Updated Content");
        request.setTags(newTagNames);
        
        postApplicationService.updatePost(testUserId, testPostId, request);
        
        // Then: 验证删除所有旧标签，添加所有新标签（即使有重复）
        verify(postTagRepository, times(1)).detachAllByPostId(testPostId);
        verify(tagDomainService, times(1)).findOrCreateBatch(newTagNames);
        verify(postTagRepository, times(1)).attachBatch(eq(testPostId), argThat(ids -> 
            ids != null && ids.size() == 3
        ));
    }

    /**
     * 测试更新不存在的文章
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void updateNonExistentPostShouldThrowException() {
        // Given: 文章不存在
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.empty());
        
        // When & Then: 更新应该抛出异常
        com.blog.post.interfaces.dto.request.UpdatePostRequest request = 
            new com.blog.post.interfaces.dto.request.UpdatePostRequest();
        request.setTitle("Updated Title");
        request.setContent("Updated Content");
        request.setTags(List.of("Java"));
        
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            postApplicationService.updatePost(testUserId, testPostId, request);
        });
        
        // 验证异常消息
        assertTrue(exception.getMessage().contains("不存在"),
            "异常消息应该提示文章不存在: " + exception.getMessage());
        
        // 验证没有进行任何标签操作
        verify(postTagRepository, never()).detachAllByPostId(anyLong());
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
    }

    /**
     * 测试更新他人文章
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void updateOtherUserPostShouldThrowException() {
        // Given: 文章属于其他用户
        Long otherUserId = 9999L;
        Post otherUserPost = Post.createDraft(testPostId, otherUserId, "Other User's Post");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(otherUserPost));
        
        // When & Then: 更新应该抛出异常
        com.blog.post.interfaces.dto.request.UpdatePostRequest request = 
            new com.blog.post.interfaces.dto.request.UpdatePostRequest();
        request.setTitle("Updated Title");
        request.setContent("Updated Content");
        request.setTags(List.of("Java"));
        
        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> {
            postApplicationService.updatePost(testUserId, testPostId, request);
        });
        
        // 验证异常消息
        assertTrue(exception.getMessage().contains("无权"),
            "异常消息应该提示无权操作: " + exception.getMessage());
        
        // 验证没有进行任何标签操作
        verify(postTagRepository, never()).detachAllByPostId(anyLong());
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
    }

    // ==================== replacePostTags 方法测试 ====================

    /**
     * 测试 replacePostTags - 正常替换标签
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void replacePostTagsShouldReplaceAllTags() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Test Post");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        // Mock: 新标签
        List<String> newTagNames = List.of("Java", "Spring", "DDD");
        List<Tag> mockTags = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        
        for (int i = 0; i < newTagNames.size(); i++) {
            Long tagId = 3000L + i;
            String slug = newTagNames.get(i).toLowerCase();
            Tag mockTag = Tag.create(tagId, newTagNames.get(i), slug);
            mockTags.add(mockTag);
            tagIds.add(tagId);
        }
        
        when(tagDomainService.findOrCreateBatch(newTagNames)).thenReturn(mockTags);
        doNothing().when(postTagRepository).detachAllByPostId(testPostId);
        doNothing().when(postTagRepository).attachBatch(eq(testPostId), anyList());
        
        // When: 替换标签
        postApplicationService.replacePostTags(testUserId, testPostId, newTagNames);
        
        // Then: 验证操作顺序
        // 1. 先删除旧标签
        verify(postTagRepository, times(1)).detachAllByPostId(testPostId);
        
        // 2. 查找或创建新标签
        verify(tagDomainService, times(1)).findOrCreateBatch(newTagNames);
        
        // 3. 添加新标签
        verify(postTagRepository, times(1)).attachBatch(eq(testPostId), argThat(ids -> 
            ids != null && ids.size() == 3 && ids.containsAll(tagIds)
        ));
    }

    /**
     * 测试 replacePostTags - 替换为空标签列表
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void replacePostTagsWithEmptyListShouldRemoveAllTags() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Test Post");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        doNothing().when(postTagRepository).detachAllByPostId(testPostId);
        
        // When: 替换为空列表
        postApplicationService.replacePostTags(testUserId, testPostId, new ArrayList<>());
        
        // Then: 验证只删除了旧标签，没有添加新标签
        verify(postTagRepository, times(1)).detachAllByPostId(testPostId);
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
    }

    /**
     * 测试 replacePostTags - 替换为 null
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void replacePostTagsWithNullShouldRemoveAllTags() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Test Post");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        doNothing().when(postTagRepository).detachAllByPostId(testPostId);
        
        // When: 替换为 null
        postApplicationService.replacePostTags(testUserId, testPostId, null);
        
        // Then: 验证只删除了旧标签，没有添加新标签
        verify(postTagRepository, times(1)).detachAllByPostId(testPostId);
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
    }

    /**
     * 测试 replacePostTags - 超过10个标签应被拒绝
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.5
     */
    @Test
    void replacePostTagsWithMoreThan10TagsShouldBeRejected() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Test Post");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        // 生成11个标签
        List<String> tagNames = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            tagNames.add("tag" + i);
        }
        
        // When & Then: 应该抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            postApplicationService.replacePostTags(testUserId, testPostId, tagNames);
        });
        
        // 验证异常消息
        assertTrue(exception.getMessage().contains("10") || 
                   exception.getMessage().contains("标签"),
            "异常消息应该提示标签数量限制: " + exception.getMessage());
        
        // 验证没有进行任何标签操作（因为在验证阶段就失败了）
        verify(postTagRepository, never()).detachAllByPostId(anyLong());
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
    }

    /**
     * 测试 replacePostTags - 正好10个标签应成功
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.5
     */
    @Test
    void replacePostTagsWithExactly10TagsShouldSucceed() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Test Post");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        // 正好10个标签
        List<String> tagNames = new ArrayList<>();
        List<Tag> mockTags = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            String tagName = "tag" + i;
            tagNames.add(tagName);
            
            Long tagId = 3000L + i;
            String slug = tagName.toLowerCase();
            Tag mockTag = Tag.create(tagId, tagName, slug);
            mockTags.add(mockTag);
            tagIds.add(tagId);
        }
        
        when(tagDomainService.findOrCreateBatch(tagNames)).thenReturn(mockTags);
        doNothing().when(postTagRepository).detachAllByPostId(testPostId);
        doNothing().when(postTagRepository).attachBatch(eq(testPostId), anyList());
        
        // When: 替换标签
        postApplicationService.replacePostTags(testUserId, testPostId, tagNames);
        
        // Then: 验证成功
        verify(postTagRepository, times(1)).detachAllByPostId(testPostId);
        verify(tagDomainService, times(1)).findOrCreateBatch(tagNames);
        verify(postTagRepository, times(1)).attachBatch(eq(testPostId), argThat(ids -> 
            ids != null && ids.size() == 10
        ));
    }

    /**
     * 测试 replacePostTags - 替换不存在的文章
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void replacePostTagsForNonExistentPostShouldThrowException() {
        // Given: 文章不存在
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.empty());
        
        // When & Then: 应该抛出异常
        List<String> tagNames = List.of("Java", "Spring");
        
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            postApplicationService.replacePostTags(testUserId, testPostId, tagNames);
        });
        
        // 验证异常消息
        assertTrue(exception.getMessage().contains("不存在"),
            "异常消息应该提示文章不存在: " + exception.getMessage());
        
        // 验证没有进行任何标签操作
        verify(postTagRepository, never()).detachAllByPostId(anyLong());
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
    }

    /**
     * 测试 replacePostTags - 替换他人文章的标签
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void replacePostTagsForOtherUserPostShouldThrowException() {
        // Given: 文章属于其他用户
        Long otherUserId = 9999L;
        Post otherUserPost = Post.createDraft(testPostId, otherUserId, "Other User's Post");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(otherUserPost));
        
        // When & Then: 应该抛出异常
        List<String> tagNames = List.of("Java", "Spring");
        
        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> {
            postApplicationService.replacePostTags(testUserId, testPostId, tagNames);
        });
        
        // 验证异常消息
        assertTrue(exception.getMessage().contains("无权"),
            "异常消息应该提示无权操作: " + exception.getMessage());
        
        // 验证没有进行任何标签操作
        verify(postTagRepository, never()).detachAllByPostId(anyLong());
        verify(tagDomainService, never()).findOrCreateBatch(anyList());
        verify(postTagRepository, never()).attachBatch(anyLong(), anyList());
    }

    /**
     * 测试 replacePostTags - 替换为相同的标签（幂等性）
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void replacePostTagsWithSameTagsShouldBeIdempotent() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Test Post");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        // Mock: 相同的标签
        List<String> tagNames = List.of("Java", "Spring");
        List<Tag> mockTags = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        
        for (int i = 0; i < tagNames.size(); i++) {
            Long tagId = 3000L + i;
            String slug = tagNames.get(i).toLowerCase();
            Tag mockTag = Tag.create(tagId, tagNames.get(i), slug);
            mockTags.add(mockTag);
            tagIds.add(tagId);
        }
        
        when(tagDomainService.findOrCreateBatch(tagNames)).thenReturn(mockTags);
        doNothing().when(postTagRepository).detachAllByPostId(testPostId);
        doNothing().when(postTagRepository).attachBatch(eq(testPostId), anyList());
        
        // When: 第一次替换
        postApplicationService.replacePostTags(testUserId, testPostId, tagNames);
        
        // When: 第二次替换（相同的标签）
        postApplicationService.replacePostTags(testUserId, testPostId, tagNames);
        
        // Then: 验证两次操作都执行了（删除旧标签 + 添加新标签）
        verify(postTagRepository, times(2)).detachAllByPostId(testPostId);
        verify(tagDomainService, times(2)).findOrCreateBatch(tagNames);
        verify(postTagRepository, times(2)).attachBatch(eq(testPostId), anyList());
    }

    /**
     * 测试 replacePostTags - 替换为部分重叠的标签
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void replacePostTagsWithPartiallyOverlappingTagsShouldReplaceAll() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Test Post");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        // Mock: 新标签（部分重叠）
        List<String> newTagNames = List.of("Java", "Python", "Go");
        List<Tag> mockTags = new ArrayList<>();
        List<Long> tagIds = new ArrayList<>();
        
        for (int i = 0; i < newTagNames.size(); i++) {
            Long tagId = 3000L + i;
            String slug = newTagNames.get(i).toLowerCase();
            Tag mockTag = Tag.create(tagId, newTagNames.get(i), slug);
            mockTags.add(mockTag);
            tagIds.add(tagId);
        }
        
        when(tagDomainService.findOrCreateBatch(newTagNames)).thenReturn(mockTags);
        doNothing().when(postTagRepository).detachAllByPostId(testPostId);
        doNothing().when(postTagRepository).attachBatch(eq(testPostId), anyList());
        
        // When: 替换标签
        postApplicationService.replacePostTags(testUserId, testPostId, newTagNames);
        
        // Then: 验证删除所有旧标签，添加所有新标签
        verify(postTagRepository, times(1)).detachAllByPostId(testPostId);
        verify(tagDomainService, times(1)).findOrCreateBatch(newTagNames);
        verify(postTagRepository, times(1)).attachBatch(eq(testPostId), argThat(ids -> 
            ids != null && ids.size() == 3 && ids.containsAll(tagIds)
        ));
    }

    /**
     * 测试 replacePostTags - 替换为包含重复标签名称的列表
     * 
     * Feature: post-tag-need
     * Validates: Requirements 4.2.1
     */
    @Test
    void replacePostTagsWithDuplicateNamesShouldDeduplicate() {
        // Given: 已存在的文章
        Post existingPost = Post.createDraft(testPostId, testUserId, "Test Post");
        when(postRepository.findById(testPostId)).thenReturn(java.util.Optional.of(existingPost));
        
        // Mock: 包含重复标签的列表
        String baseTagName = "Java";
        List<String> tagNames = List.of(
            baseTagName,
            baseTagName.toUpperCase(),
            baseTagName.toLowerCase(),
            " " + baseTagName + " "
        );
        
        // TagDomainService 应该去重并只返回一个 Tag
        String slug = baseTagName.toLowerCase();
        Tag mockTag = Tag.create(3000L, baseTagName, slug);
        when(tagDomainService.findOrCreateBatch(tagNames)).thenReturn(List.of(mockTag));
        
        doNothing().when(postTagRepository).detachAllByPostId(testPostId);
        doNothing().when(postTagRepository).attachBatch(eq(testPostId), anyList());
        
        // When: 替换标签
        postApplicationService.replacePostTags(testUserId, testPostId, tagNames);
        
        // Then: 验证 TagDomainService 被调用（它负责去重）
        verify(tagDomainService, times(1)).findOrCreateBatch(tagNames);
        
        // 验证只添加了一个标签
        verify(postTagRepository, times(1)).attachBatch(eq(testPostId), argThat(ids -> 
            ids != null && ids.size() == 1 && ids.contains(3000L)
        ));
    }
}
