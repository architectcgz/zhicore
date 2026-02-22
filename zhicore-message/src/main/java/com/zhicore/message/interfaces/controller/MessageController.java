package com.zhicore.message.interfaces.controller;

import com.zhicore.common.result.ApiResponse;
import com.zhicore.message.application.dto.MessageVO;
import com.zhicore.message.application.service.MessageApplicationService;
import com.zhicore.message.interfaces.dto.request.SendMessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "消息管理", description = "私信发送、撤回、历史记录查询等相关接口")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Validated
public class MessageController {

    private final MessageApplicationService messageApplicationService;

    /**
     * 发送消息
     *
     * @param request 发送消息请求
     * @return 消息视图对象
     */
    @Operation(summary = "发送消息", description = "向指定用户发送私信消息")
    @PostMapping
    public ApiResponse<MessageVO> sendMessage(
            @Parameter(description = "发送消息请求", required = true)
            @Valid @RequestBody SendMessageRequest request) {
        MessageVO message = messageApplicationService.sendMessage(
                request.getReceiverId(),
                request.getType(),
                request.getContent(),
                request.getMediaUrl()
        );
        return ApiResponse.success(message);
    }

    /**
     * 撤回消息
     *
     * @param messageId 消息ID
     * @return 操作结果
     */
    @Operation(summary = "撤回消息", description = "撤回已发送的消息（限时2分钟内）")
    @PostMapping("/{messageId}/recall")
    public ApiResponse<Void> recallMessage(
            @Parameter(description = "消息ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "消息ID必须为正数") Long messageId) {
        messageApplicationService.recallMessage(messageId);
        return ApiResponse.success();
    }

    /**
     * 查询消息历史
     *
     * @param conversationId 会话ID
     * @param cursor 游标
     * @param limit 数量限制
     * @return 消息列表
     */
    @Operation(summary = "查询消息历史", description = "分页查询指定会话的历史消息记录")
    @GetMapping("/conversation/{conversationId}")
    public ApiResponse<List<MessageVO>> getMessageHistory(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "会话ID必须为正数") Long conversationId,
            @Parameter(description = "游标，用于分页查询", example = "100")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") int limit) {
        List<MessageVO> messages = messageApplicationService.getMessageHistory(
                conversationId, cursor, limit);
        return ApiResponse.success(messages);
    }

    /**
     * 标记消息为已读
     *
     * @param conversationId 会话ID
     * @return 操作结果
     */
    @Operation(summary = "标记消息为已读", description = "将指定会话的所有未读消息标记为已读")
    @PostMapping("/conversation/{conversationId}/read")
    public ApiResponse<Void> markAsRead(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "会话ID必须为正数") Long conversationId) {
        messageApplicationService.markAsRead(conversationId);
        return ApiResponse.success();
    }

    /**
     * 获取未读消息数
     *
     * @return 未读消息数
     */
    @Operation(summary = "获取未读消息数", description = "获取当前用户的未读消息总数")
    @GetMapping("/unread-count")
    public ApiResponse<Integer> getUnreadCount() {
        int count = messageApplicationService.getUnreadCount();
        return ApiResponse.success(count);
    }
}
