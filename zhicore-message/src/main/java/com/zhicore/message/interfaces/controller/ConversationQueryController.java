package com.zhicore.message.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.message.application.dto.ConversationVO;
import com.zhicore.message.application.service.query.ConversationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话读控制器。
 */
@Tag(name = "会话读接口", description = "会话列表、会话详情等查询接口")
@RestController
@RequestMapping({"/api/v1/messages/conversations", "/api/v1/conversations"})
@RequiredArgsConstructor
@Validated
public class ConversationQueryController {

    private final ConversationQueryService conversationQueryService;

    /**
     * 获取会话列表
     *
     * @param cursor 游标（会话ID）
     * @param limit 数量限制
     * @return 会话列表
     */
    @Operation(summary = "获取会话列表", description = "分页获取当前用户的会话列表")
    @GetMapping
    public ApiResponse<List<ConversationVO>> getConversationList(
            @Parameter(description = "游标，用于分页查询", example = "100")
            @RequestParam(required = false) @Min(value = 1, message = "游标必须为正数") Long cursor,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须为正数") int limit) {
        UserContext.requireUserId();
        List<ConversationVO> conversations = conversationQueryService.getConversationList(cursor, limit);
        return ApiResponse.success(conversations);
    }

    /**
     * 获取会话详情
     *
     * @param conversationId 会话ID
     * @return 会话视图对象
     */
    @Operation(summary = "获取会话详情", description = "根据会话ID获取会话详细信息")
    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationVO> getConversation(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "会话ID必须为正数") Long conversationId) {
        UserContext.requireUserId();
        ConversationVO conversation = conversationQueryService.getConversation(conversationId);
        return ApiResponse.success(conversation);
    }

    /**
     * 根据对方用户ID获取会话
     *
     * @param userId 对方用户ID
     * @return 会话视图对象
     */
    @Operation(summary = "根据用户ID获取会话", description = "根据对方用户ID获取或创建会话")
    @GetMapping("/user/{userId}")
    public ApiResponse<ConversationVO> getConversationByUser(
            @Parameter(description = "对方用户ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "用户ID必须为正数") Long userId) {
        UserContext.requireUserId();
        ConversationVO conversation = conversationQueryService.getConversationByUser(userId);
        return ApiResponse.success(conversation);
    }

    /**
     * 获取会话数量
     *
     * @return 会话数量
     */
    @Operation(summary = "获取会话数量", description = "获取当前用户的会话总数")
    @GetMapping("/count")
    public ApiResponse<Integer> getConversationCount() {
        UserContext.requireUserId();
        int count = conversationQueryService.getConversationCount();
        return ApiResponse.success(count);
    }
}
