package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.dto.CommentManageVO;
import com.zhicore.admin.application.service.CommentManageQueryService;
import com.zhicore.admin.infrastructure.security.RequireAdmin;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论管理查询控制器。
 */
@Tag(name = "评论管理", description = "管理员评论查询接口")
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class CommentManageQueryController {

    private final CommentManageQueryService commentManageQueryService;

    @Operation(summary = "查询评论列表", description = "分页查询评论列表，支持按关键词、文章ID、用户ID筛选")
    @GetMapping
    public ApiResponse<PageResult<CommentManageVO>> listComments(
            @Parameter(description = "搜索关键词（评论内容）", example = "很好")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "文章ID", example = "2001")
            @RequestParam(value = "postId", required = false) Long postId,
            @Parameter(description = "用户ID", example = "1001")
            @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        return ApiResponse.success(commentManageQueryService.listComments(keyword, postId, userId, page, size));
    }
}
