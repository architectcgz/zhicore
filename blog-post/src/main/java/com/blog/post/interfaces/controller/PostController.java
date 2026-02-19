package com.blog.post.interfaces.controller;

import com.blog.common.context.UserContext;
import com.blog.common.result.ApiResponse;
import com.blog.common.result.HybridPageRequest;
import com.blog.common.result.HybridPageResult;
import com.blog.post.application.dto.PostBriefVO;
import com.blog.post.application.dto.PostVO;
import com.blog.post.application.service.PostApplicationService;
import com.blog.post.domain.model.PostStatus;
import com.blog.post.interfaces.dto.request.CreatePostRequest;
import com.blog.post.interfaces.dto.request.SchedulePublishRequest;
import com.blog.post.interfaces.dto.request.UpdatePostRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章控制器
 *
 * @author Blog Team
 */
@Tag(name = "文章管理", description = "文章CRUD、发布、草稿管理等相关接口")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostApplicationService postApplicationService;

    /**
     * 创建文章（草稿）
     */
    @Operation(summary = "创建文章", description = "创建新文章，默认状态为草稿")
    @PostMapping
    public ApiResponse<Long> createPost(
            @Parameter(description = "创建文章请求", required = true)
            @Valid @RequestBody CreatePostRequest request) {
        Long userId = UserContext.getUserId();
        Long postId = postApplicationService.createPost(userId, request);
        return ApiResponse.success(postId);
    }

    /**
     * 更新文章
     */
    @Operation(summary = "更新文章", description = "更新文章内容、标题、封面等信息")
    @PutMapping("/{postId}")
    public ApiResponse<Void> updatePost(
            @Parameter(description = "文章ID", required = true)
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "更新文章请求", required = true)
            @Valid @RequestBody UpdatePostRequest request) {
        Long userId = UserContext.getUserId();
        postApplicationService.updatePost(userId, postId, request);
        return ApiResponse.success();
    }

    /**
     * 发布文章
     */
    @Operation(summary = "发布文章", description = "将草稿状态的文章发布为公开可见")
    @PostMapping("/{postId}/publish")
    public ApiResponse<Void> publishPost(
            @Parameter(description = "文章ID", required = true)
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        postApplicationService.publishPost(userId, postId);
        return ApiResponse.success();
    }

    /**
     * 撤回文章
     */
    @PostMapping("/{postId}/unpublish")
    public ApiResponse<Void> unpublishPost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        postApplicationService.unpublishPost(userId, postId);
        return ApiResponse.success();
    }

    /**
     * 定时发布文章
     */
    @PostMapping("/{postId}/schedule")
    public ApiResponse<Void> schedulePublish(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Valid @RequestBody SchedulePublishRequest request) {
        Long userId = UserContext.getUserId();
        postApplicationService.schedulePublish(userId, postId, request.getScheduledAt());
        return ApiResponse.success();
    }

    /**
     * 取消定时发布
     */
    @DeleteMapping("/{postId}/schedule")
    public ApiResponse<Void> cancelSchedule(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        postApplicationService.cancelSchedule(userId, postId);
        return ApiResponse.success();
    }

    /**
     * 删除文章
     */
    @Operation(summary = "删除文章", description = "删除指定文章（软删除）")
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @Parameter(description = "文章ID", required = true)
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        postApplicationService.deletePost(userId, postId);
        return ApiResponse.success();
    }

    /**
     * 获取文章详情（公开）
     */
    @Operation(summary = "获取文章详情", description = "获取已发布文章的详细信息")
    @GetMapping("/{postId}")
    public ApiResponse<PostVO> getPost(
            @Parameter(description = "文章ID", required = true)
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        PostVO post = postApplicationService.getPostById(postId);
        return ApiResponse.success(post);
    }

    /**
     * 获取用户的文章详情（包括草稿）
     */
    @GetMapping("/my/{postId}")
    public ApiResponse<PostVO> getMyPost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        PostVO post = postApplicationService.getUserPostById(userId, postId);
        return ApiResponse.success(post);
    }

    /**
     * 获取用户的文章列表
     */
    @GetMapping("/my")
    public ApiResponse<List<PostBriefVO>> getMyPosts(
            @RequestParam(defaultValue = "DRAFT") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        PostStatus postStatus = PostStatus.valueOf(status);
        List<PostBriefVO> posts = postApplicationService.getUserPosts(userId, postStatus, page, size);
        return ApiResponse.success(posts);
    }

    /**
     * 获取已发布文章列表（分页）
     */
    @Operation(summary = "获取已发布文章列表", description = "分页获取所有已发布的文章列表")
    @GetMapping
    public ApiResponse<HybridPageResult<PostBriefVO>> getPublishedPosts(
            @Parameter(description = "页码", example = "1")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "排序方式", example = "latest")
            @RequestParam(required = false) String sort,
            @Parameter(description = "文章状态", example = "PUBLISHED")
            @RequestParam(required = false) String status) {
        // 使用混合分页接口
        HybridPageRequest request = new HybridPageRequest();
        request.setPage(page != null ? page : 1);
        request.setSize(size);
        
        HybridPageResult<PostBriefVO> result = postApplicationService.getPublishedPostsHybrid(request);
        return ApiResponse.success(result);
    }

    /**
     * 获取已发布文章列表（游标分页）
     */
    @GetMapping("/cursor")
    public ApiResponse<List<PostBriefVO>> getPublishedPostsCursor(
            @RequestParam(required = false) LocalDateTime cursor,
            @RequestParam(defaultValue = "20") int size) {
        List<PostBriefVO> posts = postApplicationService.getPublishedPostsCursor(cursor, size);
        return ApiResponse.success(posts);
    }

    /**
     * 获取已发布文章列表（混合分页）
     * 
     * 分页策略：
     * - 页码 ≤ 5：使用 Offset 分页，支持跳页
     * - 页码 > 5：自动切换为 Cursor 分页，性能更好
     * 
     * 使用方式：
     * - Offset 模式：GET /api/v1/posts/hybrid?page=1&size=20
     * - Cursor 模式：GET /api/v1/posts/hybrid?cursor=xxx&size=20
     */
    @GetMapping("/hybrid")
    public ApiResponse<HybridPageResult<PostBriefVO>> getPublishedPostsHybrid(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        HybridPageRequest request = new HybridPageRequest();
        request.setPage(page);
        request.setCursor(cursor);
        request.setSize(size);
        
        HybridPageResult<PostBriefVO> result = postApplicationService.getPublishedPostsHybrid(request);
        return ApiResponse.success(result);
    }

    /**
     * 批量获取文章信息
     */
    @PostMapping("/batch")
    public ApiResponse<Map<Long, com.blog.api.dto.post.PostDTO>> batchGetPosts(@RequestBody Set<Long> postIds) {
        Map<Long, com.blog.api.dto.post.PostDTO> result = postApplicationService.batchGetPosts(postIds);
        return ApiResponse.success(result);
    }

    /**
     * 获取文章内容（延迟加载）
     * 仅返回文章内容，不包含元数据
     * 用于前端按需加载文章内容，提升列表页性能
     */
    @GetMapping("/{postId}/content")
    public ApiResponse<com.blog.post.infrastructure.mongodb.document.PostContent> getPostContent(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        com.blog.post.infrastructure.mongodb.document.PostContent content = 
            postApplicationService.getPostContent(postId);
        return ApiResponse.success(content);
    }

    // ==================== 草稿管理 API ====================

    /**
     * 保存草稿
     * 支持自动保存和手动保存，每个用户每篇文章只保留一份草稿（Upsert模式）
     * 
     * @param postId 文章ID
     * @param request 保存草稿请求
     * @return 成功响应
     */
    @Operation(summary = "保存草稿", description = "保存文章草稿，支持自动保存和手动保存")
    @PostMapping("/{postId}/draft")
    public ApiResponse<Void> saveDraft(
            @Parameter(description = "文章ID", required = true)
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "保存草稿请求", required = true)
            @Valid @RequestBody com.blog.post.interfaces.dto.request.SaveDraftRequest request) {
        Long userId = UserContext.getUserId();
        postApplicationService.saveDraft(postId, userId, request);
        return ApiResponse.success();
    }

    /**
     * 获取草稿
     * 查询指定文章的最新草稿
     * 
     * @param postId 文章ID
     * @return 草稿内容
     */
    @GetMapping("/{postId}/draft")
    public ApiResponse<com.blog.post.interfaces.dto.response.DraftVO> getDraft(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        com.blog.post.interfaces.dto.response.DraftVO draft = 
            postApplicationService.getDraft(postId, userId);
        return ApiResponse.success(draft);
    }

    /**
     * 获取用户所有草稿
     * 按保存时间倒序返回用户的所有草稿
     * 
     * @return 草稿列表
     */
    @GetMapping("/drafts")
    public ApiResponse<List<com.blog.post.interfaces.dto.response.DraftVO>> getUserDrafts() {
        Long userId = UserContext.getUserId();
        List<com.blog.post.interfaces.dto.response.DraftVO> drafts = 
            postApplicationService.getUserDrafts(userId);
        return ApiResponse.success(drafts);
    }

    /**
     * 删除草稿
     * 当用户主动删除草稿或发布文章时调用
     * 
     * @param postId 文章ID
     * @return 成功响应
     */
    @DeleteMapping("/{postId}/draft")
    public ApiResponse<Void> deleteDraft(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        postApplicationService.deleteDraft(postId, userId);
        return ApiResponse.success();
    }

    // ==================== 标签管理 API ====================

    /**
     * 为文章添加标签
     * 
     * 为指定文章添加标签，会替换现有的所有标签。
     * 
     * 功能说明：
     * - 支持同时添加多个标签（最多 10 个）
     * - 标签名称会自动规范化为 slug（小写、连字符分隔）
     * - 不存在的标签会自动创建
     * - 重复的标签会自动去重
     * - 只有文章作者可以修改标签
     * 
     * Requirements: 4.2.1
     * 
     * @param postId 文章ID
     * @param request 添加标签请求
     * @return 成功响应
     */
    @Operation(
        summary = "为文章添加标签",
        description = "为指定文章添加标签，会替换现有标签。支持同时添加多个标签（最多10个），不存在的标签会自动创建。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "成功添加标签"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误（标签数量超过限制、标签名称不合法等）"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未认证"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "权限不足（只有文章作者可以修改标签）"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "文章不存在"
        )
    })
    @PostMapping("/{postId}/tags")
    public ApiResponse<Void> attachTags(
            @Parameter(description = "文章ID", required = true, example = "1234567890123456789")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "添加标签请求", required = true)
            @Valid @RequestBody com.blog.post.interfaces.dto.request.AttachTagsRequest request) {
        Long userId = UserContext.getUserId();
        postApplicationService.replacePostTags(userId, postId, request.getTags());
        return ApiResponse.success();
    }

    /**
     * 移除文章的标签
     * 
     * 移除文章的指定标签。如果标签不存在，操作会被忽略。
     * 
     * 功能说明：
     * - 根据 slug 移除指定标签
     * - 只有文章作者可以移除标签
     * - 如果标签不存在，不会报错
     * 
     * Requirements: 4.2.1
     * 
     * @param postId 文章ID
     * @param slug 标签slug
     * @return 成功响应
     */
    @Operation(
        summary = "移除文章的标签",
        description = "移除文章的指定标签。根据 slug 移除，如果标签不存在则忽略。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "成功移除标签"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未认证"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "权限不足（只有文章作者可以移除标签）"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "文章不存在"
        )
    })
    @DeleteMapping("/{postId}/tags/{slug}")
    public ApiResponse<Void> detachTag(
            @Parameter(description = "文章ID", required = true, example = "1234567890123456789")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "标签 slug（URL 友好标识）", required = true, example = "spring-boot")
            @PathVariable String slug) {
        Long userId = UserContext.getUserId();
        
        // 获取当前文章的所有标签
        List<com.blog.post.application.dto.TagDTO> currentTags = postApplicationService.getPostTags(postId);
        
        // 过滤掉要删除的标签
        List<String> remainingTagNames = currentTags.stream()
                .filter(tag -> !tag.getSlug().equals(slug))
                .map(com.blog.post.application.dto.TagDTO::getName)
                .collect(java.util.stream.Collectors.toList());
        
        // 替换标签
        postApplicationService.replacePostTags(userId, postId, remainingTagNames);
        return ApiResponse.success();
    }

    /**
     * 获取文章的标签列表
     * 
     * 获取指定文章的所有标签，按创建时间排序。
     * 
     * 功能说明：
     * - 返回文章的所有标签
     * - 包含标签的完整信息（ID、名称、slug、描述等）
     * - 公开接口，无需认证
     * 
     * Requirements: 4.2.1
     * 
     * @param postId 文章ID
     * @return 标签列表
     */
    @Operation(
        summary = "获取文章的标签列表",
        description = "获取指定文章的所有标签，按创建时间排序。公开接口，无需认证。"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "成功获取标签列表"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "文章不存在"
        )
    })
    @GetMapping("/{postId}/tags")
    public ApiResponse<List<com.blog.post.application.dto.TagDTO>> getPostTags(
            @Parameter(description = "文章ID", required = true, example = "1234567890123456789")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        List<com.blog.post.application.dto.TagDTO> tags = postApplicationService.getPostTags(postId);
        return ApiResponse.success(tags);
    }
}
