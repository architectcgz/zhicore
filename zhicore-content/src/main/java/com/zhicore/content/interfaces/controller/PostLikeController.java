package com.zhicore.content.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.service.PostLikeApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文章点赞控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "文章点赞管理", description = "文章点赞功能，包括点赞文章、取消点赞、查询点赞状态、统计点赞数等功能")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostLikeController {

    private final PostLikeApplicationService likeService;

    /**
     * 点赞文章
     */
    @Operation(
            summary = "点赞文章",
            description = "用户点赞指定文章"
    )
    @PostMapping("/{postId}/like")
    public ApiResponse<Void> likePost(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        likeService.likePost(userId, postId);
        return ApiResponse.success();
    }

    /**
     * 取消点赞
     */
    @Operation(
            summary = "取消点赞",
            description = "用户取消对指定文章的点赞"
    )
    @DeleteMapping("/{postId}/like")
    public ApiResponse<Void> unlikePost(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        likeService.unlikePost(userId, postId);
        return ApiResponse.success();
    }

    /**
     * 检查点赞状态
     */
    @Operation(
            summary = "检查点赞状态",
            description = "检查当前用户是否已点赞指定文章"
    )
    @GetMapping("/{postId}/like/status")
    public ApiResponse<Boolean> checkLikeStatus(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        boolean liked = likeService.isLiked(userId, postId);
        return ApiResponse.success(liked);
    }

    /**
     * 批量检查点赞状态
     */
    @Operation(
            summary = "批量检查点赞状态",
            description = "批量检查当前用户对多篇文章的点赞状态"
    )
    @PostMapping("/like/batch-status")
    public ApiResponse<Map<Long, Boolean>> batchCheckLikeStatus(
            @Parameter(description = "文章ID列表", required = true)
            @RequestBody List<Long> postIds) {
        Long userId = UserContext.getUserId();
        Map<Long, Boolean> likedMap = likeService.batchCheckLiked(userId, postIds);
        return ApiResponse.success(likedMap);
    }

    /**
     * 获取文章点赞数
     */
    @Operation(
            summary = "获取文章点赞数",
            description = "获取指定文章的总点赞数"
    )
    @GetMapping("/{postId}/like/count")
    public ApiResponse<Integer> getLikeCount(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        int count = likeService.getLikeCount(postId);
        return ApiResponse.success(count);
    }
}
