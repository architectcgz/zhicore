package com.zhicore.content.interfaces.controller;

import com.zhicore.api.dto.admin.PostManageDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.service.AdminPostQueryFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端文章查询控制器。
 */
@Tag(name = "管理员-文章管理", description = "管理员文章查询功能")
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class AdminPostQueryController {

    private final AdminPostQueryFacade adminPostQueryFacade;

    @Operation(summary = "查询文章列表", description = "管理员分页查询文章列表，支持关键词搜索、状态筛选和作者筛选")
    @GetMapping
    public ApiResponse<PageResult<PostManageDTO>> queryPosts(
            @Parameter(description = "搜索关键词（标题、内容等）", example = "Spring Boot")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "文章状态（DRAFT/PUBLISHED/DELETED）", example = "PUBLISHED")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "作者ID", example = "1")
            @RequestParam(value = "authorId", required = false) Long authorId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size) {

        log.info("Admin query posts: keyword={}, status={}, authorId={}, page={}, size={}",
                keyword, status, authorId, page, size);
        return ApiResponse.success(adminPostQueryFacade.queryPosts(keyword, status, authorId, page, size));
    }
}
