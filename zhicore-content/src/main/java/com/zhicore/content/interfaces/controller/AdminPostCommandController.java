package com.zhicore.content.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.service.AdminPostCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端文章写控制器。
 */
@Tag(name = "管理员-文章管理", description = "管理员文章写操作功能")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class AdminPostCommandController {

    private final AdminPostCommandService adminPostCommandService;

    @Operation(summary = "删除文章", description = "管理员删除文章（软删除），删除后文章不再对外展示")
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @Parameter(description = "文章ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "文章ID必须为正数") Long postId) {
        log.info("Admin delete post: postId={}", postId);
        adminPostCommandService.deletePost(postId);
        return ApiResponse.success();
    }
}
