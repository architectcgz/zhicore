package com.zhicore.content.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.service.query.PostLikeQueryService;
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
 * 文章点赞查询控制器。
 */
@Tag(name = "文章点赞管理", description = "文章点赞查询功能")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostLikeQueryController {

    private final PostLikeQueryService postLikeQueryService;

    @Operation(summary = "检查点赞状态", description = "检查当前用户是否已点赞指定文章")
    @GetMapping("/{postId}/like/status")
    public ApiResponse<Boolean> checkLikeStatus(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(postLikeQueryService.isLiked(userId, postId));
    }

    @Operation(summary = "批量检查点赞状态", description = "批量检查当前用户对多篇文章的点赞状态")
    @PostMapping("/like/batch-status")
    public ApiResponse<Map<Long, Boolean>> batchCheckLikeStatus(
            @Parameter(description = "文章ID列表", required = true)
            @RequestBody List<Long> postIds) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(postLikeQueryService.batchCheckLiked(userId, postIds));
    }

    @Operation(summary = "获取文章点赞数", description = "获取指定文章的总点赞数")
    @GetMapping("/{postId}/like/count")
    public ApiResponse<Integer> getLikeCount(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        return ApiResponse.success(postLikeQueryService.getLikeCount(postId));
    }
}
