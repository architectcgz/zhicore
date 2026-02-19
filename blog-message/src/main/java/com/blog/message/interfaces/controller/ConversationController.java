package com.blog.message.interfaces.controller;

import com.blog.common.result.ApiResponse;
import com.blog.message.application.dto.ConversationVO;
import com.blog.message.application.service.ConversationApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话控制器
 *
 * @author Blog Team
 */
@Tag(name = "会话管理", description = "会话列表、会话详情查询等相关接口")
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationApplicationService conversationApplicationService;

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
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") int limit) {
        List<ConversationVO> conversations = conversationApplicationService.getConversationList(cursor, limit);
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
            @PathVariable Long conversationId) {
        ConversationVO conversation = conversationApplicationService.getConversation(conversationId);
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
            @PathVariable Long userId) {
        ConversationVO conversation = conversationApplicationService.getConversationByUser(userId);
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
        int count = conversationApplicationService.getConversationCount();
        return ApiResponse.success(count);
    }
}
