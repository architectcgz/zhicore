package com.zhicore.content.interfaces.controller;

import com.zhicore.clients.dto.post.PostDTO;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.HybridPageRequest;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.content.application.query.model.PostListQuery;
import com.zhicore.content.application.query.model.PostListSort;
import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.service.PostFacadeService;
import com.zhicore.content.infrastructure.persistence.mongo.document.PostContent;
import com.zhicore.content.interfaces.dto.request.AttachTagsRequest;
import com.zhicore.content.interfaces.dto.request.CreatePostRequest;
import com.zhicore.content.interfaces.dto.request.SaveDraftRequest;
import com.zhicore.content.interfaces.dto.request.SchedulePublishRequest;
import com.zhicore.content.interfaces.dto.request.UpdatePostRequest;
import com.zhicore.content.interfaces.dto.response.DraftVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章控制器
 */
@Tag(name = "文章管理", description = "文章CRUD、发布、草稿管理等相关接口")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostFacadeService postFacadeService;

    @Operation(summary = "创建文章", description = "创建新文章，默认状态为草稿")
    @PostMapping
    public ApiResponse<Long> createPost(@Valid @RequestBody CreatePostRequest request) {
        Long userId = UserContext.getUserId();
        return ApiResponse.success(postFacadeService.createPost(userId, request));
    }

    @Operation(summary = "更新文章", description = "更新文章内容、标题、封面等信息")
    @PutMapping("/{postId}")
    public ApiResponse<Void> updatePost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Valid @RequestBody UpdatePostRequest request) {
        Long userId = UserContext.getUserId();
        postFacadeService.updatePost(userId, postId, request);
        return ApiResponse.success();
    }

    @Operation(summary = "发布文章", description = "将草稿状态的文章发布为公开可见")
    @PostMapping("/{postId}/publish")
    public ApiResponse<Void> publishPost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        postFacadeService.publishPost(userId, postId);
        return ApiResponse.success();
    }

    @PostMapping("/{postId}/unpublish")
    public ApiResponse<Void> unpublishPost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        postFacadeService.unpublishPost(userId, postId);
        return ApiResponse.success();
    }

    @PostMapping("/{postId}/schedule")
    public ApiResponse<Void> schedulePublish(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Valid @RequestBody SchedulePublishRequest request) {
        Long userId = UserContext.getUserId();
        postFacadeService.schedulePublish(userId, postId, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{postId}/schedule")
    public ApiResponse<Void> cancelSchedule(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        postFacadeService.cancelSchedule(userId, postId);
        return ApiResponse.success();
    }

    @Operation(summary = "删除文章", description = "删除指定文章（软删除）")
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        postFacadeService.deletePost(userId, postId);
        return ApiResponse.success();
    }

    @PostMapping("/{postId}/restore")
    public ApiResponse<Void> restorePost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        postFacadeService.restorePost(userId, postId);
        return ApiResponse.success();
    }

    @Operation(summary = "获取文章详情", description = "获取已发布文章的详细信息")
    @GetMapping("/{postId}")
    public ApiResponse<PostVO> getPost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        return ApiResponse.success(postFacadeService.getPost(postId));
    }

    @GetMapping("/my/{postId}")
    public ApiResponse<PostVO> getMyPost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        return ApiResponse.success(postFacadeService.getMyPost(userId, postId));
    }

    @GetMapping("/my")
    public ApiResponse<List<PostBriefVO>> getMyPosts(
            @RequestParam(defaultValue = "DRAFT") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        return ApiResponse.success(postFacadeService.getMyPosts(userId, status, page, size));
    }

    @Operation(summary = "获取已发布文章列表", description = "分页获取所有已发布的文章列表")
    @GetMapping
    public ApiResponse<HybridPageResult<PostBriefVO>> getPublishedPosts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status) {
        PostListQuery query = PostListQuery.builder()
                .page(page)
                .cursor(cursor)
                .size(size)
                .sort(PostListSort.parse(sort))
                .status(status)
                .build();
        return ApiResponse.success(postFacadeService.getPostList(query));
    }

    @GetMapping("/cursor")
    public ApiResponse<List<PostBriefVO>> getPublishedPostsCursor(
            @RequestParam(required = false) LocalDateTime cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(postFacadeService.getPublishedPostsCursor(cursor, size));
    }

    @GetMapping("/hybrid")
    public ApiResponse<HybridPageResult<PostBriefVO>> getPublishedPostsHybrid(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        HybridPageRequest request = new HybridPageRequest();
        request.setPage(page);
        request.setCursor(cursor);
        request.setSize(size);
        return ApiResponse.success(postFacadeService.getPublishedPostsHybrid(request));
    }

    @PostMapping("/batch")
    public ApiResponse<Map<Long, PostDTO>> batchGetPosts(@RequestBody Set<Long> postIds) {
        return ApiResponse.success(postFacadeService.batchGetPosts(postIds));
    }

    @GetMapping("/{postId}/content")
    public ApiResponse<PostContent> getPostContent(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        return ApiResponse.success(postFacadeService.getPostContent(postId));
    }

    @Operation(summary = "保存草稿", description = "保存文章草稿，支持自动保存和手动保存")
    @PostMapping("/{postId}/draft")
    public ApiResponse<Void> saveDraft(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Valid @RequestBody SaveDraftRequest request) {
        Long userId = UserContext.getUserId();
        postFacadeService.saveDraft(userId, postId, request);
        return ApiResponse.success();
    }

    @GetMapping("/{postId}/draft")
    public ApiResponse<DraftVO> getDraft(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        return ApiResponse.success(postFacadeService.getDraft(userId, postId));
    }

    @GetMapping("/drafts")
    public ApiResponse<List<DraftVO>> getUserDrafts() {
        Long userId = UserContext.getUserId();
        return ApiResponse.success(postFacadeService.getUserDrafts(userId));
    }

    @DeleteMapping("/{postId}/draft")
    public ApiResponse<Void> deleteDraft(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        postFacadeService.deleteDraft(userId, postId);
        return ApiResponse.success();
    }

    @Operation(
            summary = "为文章添加标签",
            description = "为指定文章添加标签，会替换现有标签。支持同时添加多个标签（最多10个），不存在的标签会自动创建。"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功添加标签"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误（标签数量超过限制、标签名称不合法等）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（只有文章作者可以修改标签）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文章不存在")
    })
    @PostMapping("/{postId}/tags")
    public ApiResponse<Void> attachTags(
            @Parameter(description = "文章ID", required = true, example = "1234567890123456789")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "添加标签请求", required = true)
            @Valid @RequestBody AttachTagsRequest request) {
        Long userId = UserContext.getUserId();
        postFacadeService.attachTags(userId, postId, request);
        return ApiResponse.success();
    }

    @Operation(summary = "移除文章的标签", description = "移除文章的指定标签。根据 slug 移除，如果标签不存在则忽略。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功移除标签"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（只有文章作者可以移除标签）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文章不存在")
    })
    @DeleteMapping("/{postId}/tags/{slug}")
    public ApiResponse<Void> detachTag(
            @Parameter(description = "文章ID", required = true, example = "1234567890123456789")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId,
            @Parameter(description = "标签 slug（URL 友好标识）", required = true, example = "spring-boot")
            @PathVariable String slug) {
        Long userId = UserContext.getUserId();
        postFacadeService.detachTag(userId, postId, slug);
        return ApiResponse.success();
    }

    @Operation(summary = "获取文章的标签列表", description = "获取指定文章的所有标签，按创建时间排序。公开接口，无需认证。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取标签列表"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文章不存在")
    })
    @GetMapping("/{postId}/tags")
    public ApiResponse<List<TagDTO>> getPostTags(
            @Parameter(description = "文章ID", required = true, example = "1234567890123456789")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        return ApiResponse.success(postFacadeService.getPostTags(postId));
    }
}


