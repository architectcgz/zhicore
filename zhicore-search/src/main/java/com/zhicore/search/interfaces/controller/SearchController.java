package com.zhicore.search.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.search.application.service.SearchApplicationService;
import com.zhicore.search.application.service.SuggestionService;
import com.zhicore.search.interfaces.dto.PostSearchVO;
import com.zhicore.search.interfaces.dto.SearchResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 搜索控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "搜索管理", description = "提供全文搜索、搜索建议、热门搜索、搜索历史等功能")
@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchApplicationService searchApplicationService;
    private final SuggestionService suggestionService;

    /**
     * 搜索文章
     *
     * @param keyword 关键词
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 搜索结果
     */
    @Operation(
            summary = "搜索文章",
            description = "根据关键词搜索文章，支持标题和内容的全文搜索，返回高亮结果和分页信息"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "搜索成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "参数验证失败"
            )
    })
    @GetMapping("/posts")
    public ApiResponse<SearchResultVO<PostSearchVO>> searchPosts(
            @Parameter(description = "搜索关键词", required = true, example = "Spring Boot")
            @RequestParam @NotBlank(message = "搜索关键词不能为空") String keyword,
            @Parameter(description = "页码（从0开始）", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "每页大小（1-100）", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        
        log.info("Search posts request: keyword={}, page={}, size={}", keyword, page, size);
        
        // 记录搜索关键词（UserContext.getUserId() 返回 Long）
        Long userId = UserContext.getUserId();
        suggestionService.recordSearch(keyword, userId != null ? String.valueOf(userId) : null);
        
        SearchResultVO<PostSearchVO> result = searchApplicationService.searchPosts(keyword, page, size);
        
        return ApiResponse.success(result);
    }

    /**
     * 获取搜索建议
     * 
     * 综合热门搜索词、用户历史和 ES 前缀匹配
     *
     * @param prefix 前缀
     * @param limit 限制数量
     * @return 建议列表
     */
    @Operation(
            summary = "获取搜索建议",
            description = "根据输入前缀获取搜索建议，综合热门搜索词、用户历史和 Elasticsearch 前缀匹配"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "参数验证失败"
            )
    })
    @SecurityRequirement(name = "bearer-jwt")
    @GetMapping("/suggest")
    public ApiResponse<List<String>> suggest(
            @Parameter(description = "搜索前缀", required = true, example = "Spring")
            @RequestParam @NotBlank(message = "前缀不能为空") String prefix,
            @Parameter(description = "返回数量限制（1-50）", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        
        log.debug("Suggest request: prefix={}, limit={}", prefix, limit);
        
        Long userId = UserContext.getUserId();
        List<String> suggestions = suggestionService.getSuggestions(prefix, userId != null ? String.valueOf(userId) : null, limit);
        
        return ApiResponse.success(suggestions);
    }

    /**
     * 获取热门搜索词
     *
     * @param limit 限制数量
     * @return 热门搜索词列表
     */
    @Operation(
            summary = "获取热门搜索词",
            description = "获取当前热门搜索关键词列表，按搜索频率排序"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/hot")
    public ApiResponse<List<String>> getHotKeywords(
            @Parameter(description = "返回数量限制（1-50）", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        
        log.debug("Hot keywords request: limit={}", limit);
        
        List<String> hotKeywords = suggestionService.getHotKeywords(limit);
        
        return ApiResponse.success(hotKeywords);
    }

    /**
     * 获取用户搜索历史
     *
     * @param limit 限制数量
     * @return 搜索历史列表
     */
    @Operation(
            summary = "获取用户搜索历史",
            description = "获取当前登录用户的搜索历史记录，按时间倒序排列"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "获取成功",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "未登录"
            )
    })
    @SecurityRequirement(name = "bearer-jwt")
    @GetMapping("/history")
    public ApiResponse<List<String>> getUserHistory(
            @Parameter(description = "返回数量限制（1-50）", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return ApiResponse.success(List.of());
        }
        
        log.debug("User history request: userId={}, limit={}", userId, limit);
        
        List<String> history = suggestionService.getUserHistory(String.valueOf(userId), limit);
        
        return ApiResponse.success(history);
    }

    /**
     * 清除用户搜索历史
     *
     * @return 操作结果
     */
    @Operation(
            summary = "清除用户搜索历史",
            description = "清除当前登录用户的所有搜索历史记录"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "清除成功"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "未登录"
            )
    })
    @SecurityRequirement(name = "bearer-jwt")
    @DeleteMapping("/history")
    public ApiResponse<Void> clearUserHistory() {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            suggestionService.clearUserHistory(String.valueOf(userId));
            log.info("Cleared search history for user: {}", userId);
        }
        return ApiResponse.success(null);
    }
}
