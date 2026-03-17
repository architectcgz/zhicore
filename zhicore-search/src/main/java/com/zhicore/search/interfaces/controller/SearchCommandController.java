package com.zhicore.search.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.search.application.service.command.SuggestionCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索写控制器。
 */
@Tag(name = "搜索管理", description = "搜索历史清理等写操作")
@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchCommandController {

    private final SuggestionCommandService suggestionCommandService;

    @Operation(summary = "清除用户搜索历史", description = "清除当前登录用户的所有搜索历史记录")
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
        Long userId = UserContext.requireUserId();
        suggestionCommandService.clearUserHistory(String.valueOf(userId));
        log.info("Cleared search history for user: {}", userId);
        return ApiResponse.success(null);
    }
}
