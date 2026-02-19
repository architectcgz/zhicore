package com.blog.post.interfaces.controller;

import com.blog.common.context.UserContext;
import com.blog.common.result.ApiResponse;
import com.blog.post.application.service.PostFavoriteApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文章收藏控制器
 *
 * @author Blog Team
 */
@Tag(name = "文章收藏管理", description = "文章收藏功能，包括收藏文章、取消收藏、查询收藏列表、收藏统计等功能")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostFavoriteController {

    private final PostFavoriteApplicationService favoriteService;

    /**
     * 收藏文章
     */
    @Operation(
            summary = "收藏文章",
            description = "用户收藏指定文章"
    )
    @PostMapping("/{postId}/favorite")
    public ApiResponse<Void> favoritePost(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        favoriteService.favoritePost(userId, postId);
        return ApiResponse.success();
    }

    /**
     * 取消收藏
     */
    @Operation(
            summary = "取消收藏",
            description = "用户取消对指定文章的收藏"
    )
    @DeleteMapping("/{postId}/favorite")
    public ApiResponse<Void> unfavoritePost(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        favoriteService.unfavoritePost(userId, postId);
        return ApiResponse.success();
    }

    /**
     * 检查收藏状态
     */
    @Operation(
            summary = "检查收藏状态",
            description = "检查当前用户是否已收藏指定文章"
    )
    @GetMapping("/{postId}/favorite/status")
    public ApiResponse<Boolean> checkFavoriteStatus(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.getUserId();
        boolean favorited = favoriteService.isFavorited(userId, postId);
        return ApiResponse.success(favorited);
    }

    /**
     * 批量检查收藏状态
     */
    @Operation(
            summary = "批量检查收藏状态",
            description = "批量检查当前用户对多篇文章的收藏状态"
    )
    @PostMapping("/favorite/batch-status")
    public ApiResponse<Map<Long, Boolean>> batchCheckFavoriteStatus(
            @Parameter(description = "文章ID列表", required = true)
            @RequestBody List<Long> postIds) {
        Long userId = UserContext.getUserId();
        Map<Long, Boolean> favoritedMap = favoriteService.batchCheckFavorited(userId, postIds);
        return ApiResponse.success(favoritedMap);
    }

    /**
     * 获取文章收藏数
     */
    @Operation(
            summary = "获取文章收藏数",
            description = "获取指定文章的总收藏数"
    )
    @GetMapping("/{postId}/favorite/count")
    public ApiResponse<Integer> getFavoriteCount(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        int count = favoriteService.getFavoriteCount(postId);
        return ApiResponse.success(count);
    }
}
