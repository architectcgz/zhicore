package com.zhicore.message.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.message.application.dto.MessageVO;
import com.zhicore.message.application.service.command.MessageCommandService;
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
 * 消息写控制器。
 */
@Tag(name = "消息写接口", description = "私信发送、撤回、已读变更等写操作接口")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Validated
public class MessageCommandController {

    private final MessageCommandService messageCommandService;

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
        UserContext.requireUserId();
        MessageVO message = messageCommandService.sendMessage(
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
        UserContext.requireUserId();
        messageCommandService.recallMessage(messageId);
        return ApiResponse.success();
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
        UserContext.requireUserId();
        messageCommandService.markAsRead(conversationId);
        return ApiResponse.success();
    }
}
