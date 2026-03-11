package com.zhicore.content.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.service.PostFavoriteCommandService;
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
 * 文章收藏写控制器。
 */
@Tag(name = "文章收藏管理", description = "文章收藏写操作功能")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostFavoriteCommandController {

    private final PostFavoriteCommandService postFavoriteCommandService;

    @Operation(summary = "收藏文章", description = "用户收藏指定文章")
    @PostMapping("/{postId}/favorite")
    public ApiResponse<Void> favoritePost(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        postFavoriteCommandService.favoritePost(userId, postId);
        return ApiResponse.success();
    }

    @Operation(summary = "取消收藏", description = "用户取消对指定文章的收藏")
    @DeleteMapping("/{postId}/favorite")
    public ApiResponse<Void> unfavoritePost(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        Long userId = UserContext.requireUserId();
        postFavoriteCommandService.unfavoritePost(userId, postId);
        return ApiResponse.success();
    }
}
