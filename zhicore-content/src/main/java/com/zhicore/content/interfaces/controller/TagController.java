package com.zhicore.content.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.application.dto.TagStatsDTO;
import com.zhicore.content.application.service.TagApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签控制器
 * 
 * 提供标签相关的 REST API，包括：
 * - 获取标签详情
 * - 获取标签列表
 * - 搜索标签
 * - 获取标签下的文章列表
 * - 获取热门标签
 *
 * @author ZhiCore Team
 */
@Tag(name = "标签管理", description = "标签查询、搜索、热门标签等相关接口")
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagApplicationService tagApplicationService;

    /**
     * 获取标签详情
     * 
     * 根据 slug 获取标签的详细信息，包括标签名称、描述、创建时间等。
     * 
     * Requirements: 4.1.4
     *
     * @param slug 标签的 URL 友好标识（如 "spring-boot"）
     * @return 标签详情
     */
    @Operation(
        summary = "获取标签详情",
        description = "根据 slug 获取标签的详细信息"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "成功获取标签详情",
            content = @Content(schema = @Schema(implementation = TagDTO.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "标签不存在"
        )
    })
    @GetMapping("/{slug}")
    public ApiResponse<TagDTO> getTag(
            @Parameter(description = "标签 slug（URL 友好标识）", required = true, example = "spring-boot")
            @PathVariable String slug) {
        log.info("Getting tag by slug: {}", slug);
        TagDTO tag = tagApplicationService.getTag(slug);
        return ApiResponse.success(tag);
    }

    /**
     * 获取标签列表（分页）
     * 
     * 分页获取所有标签列表，按创建时间倒序排序。
     * 
     * Requirements: 4.1.4
     *
     * @param page 页码（从 0 开始）
     * @param size 每页大小（1-100）
     * @return 分页结果
     */
    @Operation(
        summary = "获取标签列表",
        description = "分页获取所有标签列表，按创建时间倒序排序"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "成功获取标签列表"
        )
    })
    @GetMapping
    public ApiResponse<PageResult<TagDTO>> listTags(
            @Parameter(description = "页码（从 0 开始）", example = "0")
            @RequestParam(defaultValue = "0") 
            @Min(value = 0, message = "页码不能小于 0") 
            int page,
            @Parameter(description = "每页大小（1-100）", example = "20")
            @RequestParam(defaultValue = "20") 
            @Min(value = 1, message = "每页大小不能小于 1")
            @Max(value = 100, message = "每页大小不能大于 100")
            int size) {
        log.info("Listing tags: page={}, size={}", page, size);
        PageResult<TagDTO> result = tagApplicationService.listTags(page, size);
        return ApiResponse.success(result);
    }

    /**
     * 搜索标签
     * 
     * 根据关键词模糊搜索标签名称，返回匹配的标签列表。
     * 用于标签输入框的自动补全功能。
     * 
     * Requirements: 4.1.4
     *
     * @param keyword 搜索关键词
     * @param limit 返回数量限制（1-50）
     * @return 匹配的标签列表
     */
    @Operation(
        summary = "搜索标签",
        description = "根据关键词模糊搜索标签名称，用于自动补全"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "成功返回搜索结果"
        )
    })
    @GetMapping("/search")
    public ApiResponse<List<TagDTO>> searchTags(
            @Parameter(description = "搜索关键词", required = true, example = "spring")
            @RequestParam String keyword,
            @Parameter(description = "返回数量限制（1-50）", example = "10")
            @RequestParam(defaultValue = "10") 
            @Min(value = 1, message = "返回数量不能小于 1")
            @Max(value = 50, message = "返回数量不能大于 50")
            int limit) {
        log.info("Searching tags: keyword={}, limit={}", keyword, limit);
        List<TagDTO> tags = tagApplicationService.searchTags(keyword, limit);
        return ApiResponse.success(tags);
    }

    /**
     * 获取标签下的文章列表（分页）
     * 
     * 根据标签 slug 获取该标签下的所有文章，按发布时间倒序排序。
     * 
     * 实现策略：
     * 1. 根据 slug 查询 Tag
     * 2. 分页查询 Tag 下的 Post ID 列表
     * 3. 批量查询 Post 详情（避免 N+1 问题）
     * 4. 组装 PostDTO 列表
     * 
     * Requirements: 4.3.1, 4.3.2
     *
     * @param slug 标签 slug
     * @param page 页码（从 0 开始）
     * @param size 每页大小（1-100）
     * @return 分页结果
     */
    @Operation(
        summary = "获取标签下的文章列表",
        description = "根据标签 slug 获取该标签下的所有文章，按发布时间倒序排序"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "成功获取文章列表"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "标签不存在"
        )
    })
    @GetMapping("/{slug}/posts")
    public ApiResponse<PageResult<PostVO>> getPostsByTag(
            @Parameter(description = "标签 slug（URL 友好标识）", required = true, example = "spring-boot")
            @PathVariable String slug,
            @Parameter(description = "页码（从 0 开始）", example = "0")
            @RequestParam(defaultValue = "0") 
            @Min(value = 0, message = "页码不能小于 0") 
            int page,
            @Parameter(description = "每页大小（1-100）", example = "20")
            @RequestParam(defaultValue = "20") 
            @Min(value = 1, message = "每页大小不能小于 1")
            @Max(value = 100, message = "每页大小不能大于 100")
            int size) {
        log.info("Getting posts by tag: slug={}, page={}, size={}", slug, page, size);
        PageResult<PostVO> result = tagApplicationService.getPostsByTag(slug, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 获取热门标签
     * 
     * 获取按文章数量排序的热门标签列表，用于首页展示或标签云。
     * 
     * 实现策略：
     * 1. 查询 tag_stats 表，按 post_count 降序排序
     * 2. 批量查询 Tag 详情
     * 3. 组装 TagStatsDTO 列表
     * 
     * Requirements: 4.4
     *
     * @param limit 返回数量限制（1-50）
     * @return 热门标签列表（包含文章数量）
     */
    @Operation(
        summary = "获取热门标签",
        description = "获取按文章数量排序的热门标签列表，用于首页展示或标签云"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "成功获取热门标签列表"
        )
    })
    @GetMapping("/hot")
    public ApiResponse<List<TagStatsDTO>> getHotTags(
            @Parameter(description = "返回数量限制（1-50）", example = "10")
            @RequestParam(defaultValue = "10") 
            @Min(value = 1, message = "返回数量不能小于 1")
            @Max(value = 50, message = "返回数量不能大于 50")
            int limit) {
        log.info("Getting hot tags: limit={}", limit);
        List<TagStatsDTO> hotTags = tagApplicationService.getHotTags(limit);
        return ApiResponse.success(hotTags);
    }
}
