package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.dto.PostManageVO;
import com.zhicore.admin.application.service.query.PostManageQueryService;
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
 * 文章管理查询控制器。
 */
@Tag(name = "文章管理", description = "管理员文章查询接口")
@RestController
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
@RequireAdmin
@Validated
public class PostManageQueryController {

    private final PostManageQueryService postManageQueryService;

    @Operation(summary = "查询文章列表", description = "分页查询文章列表，支持按关键词、状态、作者筛选")
    @GetMapping
    public ApiResponse<PageResult<PostManageVO>> listPosts(
            @Parameter(description = "搜索关键词（标题或内容）", example = "Spring Boot")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "文章状态（PUBLISHED/DRAFT/DELETED）", example = "PUBLISHED")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "作者用户ID", example = "1001")
            @RequestParam(value = "authorId", required = false) Long authorId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        return ApiResponse.success(postManageQueryService.listPosts(keyword, status, authorId, page, size));
    }
}
