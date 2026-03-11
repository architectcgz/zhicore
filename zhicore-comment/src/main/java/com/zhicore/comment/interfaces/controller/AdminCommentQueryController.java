package com.zhicore.comment.interfaces.controller;

import com.zhicore.api.dto.admin.CommentManageDTO;
import com.zhicore.comment.application.service.AdminCommentQueryService;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧评论读控制器。
 */
@Tag(name = "管理员-评论查询", description = "管理员评论查询接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/comments")
@RequiredArgsConstructor
public class AdminCommentQueryController {

    private final AdminCommentQueryService adminCommentQueryService;

    @Operation(summary = "查询评论列表", description = "管理员查询评论列表，支持按关键词、文章ID、用户ID筛选")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping
    public ApiResponse<PageResult<CommentManageDTO>> queryComments(
            @Parameter(description = "关键词，搜索评论内容", example = "测试评论")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "文章ID，筛选指定文章的评论", example = "1234567890")
            @RequestParam(value = "postId", required = false) Long postId,
            @Parameter(description = "用户ID，筛选指定用户的评论", example = "1234567890")
            @RequestParam(value = "userId", required = false) Long userId,
            @Parameter(description = "页码，从1开始", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        log.info("Admin query comments: keyword={}, postId={}, userId={}, page={}, size={}",
                keyword, postId, userId, page, size);
        return ApiResponse.success(adminCommentQueryService.queryComments(keyword, postId, userId, page, size));
    }
}
