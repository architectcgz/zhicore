package com.zhicore.content.interfaces.controller;

import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.HybridPageRequest;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.content.application.dto.DraftVO;
import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.dto.PostContentVO;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.query.model.PostListQuery;
import com.zhicore.content.application.query.model.PostListSort;
import com.zhicore.content.application.service.PostReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章读控制器。
 */
@Tag(name = "文章读接口", description = "文章详情、列表、草稿、标签等查询接口")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostQueryController {

    private final PostReadService postReadService;

    @Operation(summary = "获取文章详情", description = "获取已发布文章的详细信息")
    @GetMapping("/{postId}")
    public ApiResponse<PostVO> getPost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        return ApiResponse.success(postReadService.getPost(postId));
    }

    @GetMapping("/my/{postId}")
    public ApiResponse<PostVO> getMyPost(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(postReadService.getMyPost(userId, postId));
    }

    @GetMapping("/my")
    public ApiResponse<List<PostBriefVO>> getMyPosts(
            @RequestParam(defaultValue = "DRAFT") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(postReadService.getMyPosts(userId, status, page, size));
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
        return ApiResponse.success(postReadService.getPostList(query));
    }

    @GetMapping("/cursor")
    public ApiResponse<List<PostBriefVO>> getPublishedPostsCursor(
            @RequestParam(required = false) LocalDateTime cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(postReadService.getPublishedPostsCursor(cursor, size));
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
        return ApiResponse.success(postReadService.getPublishedPostsHybrid(request));
    }

    @PostMapping("/batch")
    public ApiResponse<Map<Long, PostDTO>> batchGetPosts(@RequestBody Set<Long> postIds) {
        return ApiResponse.success(postReadService.batchGetPosts(postIds));
    }

    @GetMapping("/{postId}/content")
    public ApiResponse<PostContentVO> getPostContent(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        return ApiResponse.success(postReadService.getPostContent(postId));
    }

    @GetMapping("/{postId}/draft")
    public ApiResponse<DraftVO> getDraft(
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(postReadService.getDraft(userId, postId));
    }

    @GetMapping("/drafts")
    public ApiResponse<List<DraftVO>> getUserDrafts() {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(postReadService.getUserDrafts(userId));
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
        return ApiResponse.success(postReadService.getPostTags(postId));
    }
}
