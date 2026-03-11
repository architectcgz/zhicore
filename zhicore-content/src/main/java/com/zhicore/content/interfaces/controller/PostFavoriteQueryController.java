package com.zhicore.content.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.service.PostFavoriteQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 文章收藏查询控制器。
 */
@Tag(name = "文章收藏管理", description = "文章收藏查询功能")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostFavoriteQueryController {

    private final PostFavoriteQueryService postFavoriteQueryService;

    @Operation(summary = "检查收藏状态", description = "检查当前用户是否已收藏指定文章")
    @GetMapping("/{postId}/favorite/status")
    public ApiResponse<Boolean> checkFavoriteStatus(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(postFavoriteQueryService.isFavorited(userId, postId));
    }

    @Operation(summary = "批量检查收藏状态", description = "批量检查当前用户对多篇文章的收藏状态")
    @PostMapping("/favorite/batch-status")
    public ApiResponse<Map<Long, Boolean>> batchCheckFavoriteStatus(
            @Parameter(description = "文章ID列表", required = true)
            @RequestBody List<Long> postIds) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(postFavoriteQueryService.batchCheckFavorited(userId, postIds));
    }

    @Operation(summary = "获取文章收藏数", description = "获取指定文章的总收藏数")
    @GetMapping("/{postId}/favorite/count")
    public ApiResponse<Integer> getFavoriteCount(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        return ApiResponse.success(postFavoriteQueryService.getFavoriteCount(postId));
    }
}
