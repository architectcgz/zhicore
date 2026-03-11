package com.zhicore.content.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.service.PostLikeCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文章点赞写控制器。
 */
@Tag(name = "文章点赞管理", description = "文章点赞写操作功能")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostLikeCommandController {

    private final PostLikeCommandService postLikeCommandService;

    @Operation(summary = "点赞文章", description = "用户点赞指定文章")
    @PostMapping("/{postId}/like")
    public ApiResponse<Void> likePost(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        postLikeCommandService.likePost(userId, postId);
        return ApiResponse.success();
    }

    @Operation(summary = "取消点赞", description = "用户取消对指定文章的点赞")
    @DeleteMapping("/{postId}/like")
    public ApiResponse<Void> unlikePost(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        postLikeCommandService.unlikePost(userId, postId);
        return ApiResponse.success();
    }
}
