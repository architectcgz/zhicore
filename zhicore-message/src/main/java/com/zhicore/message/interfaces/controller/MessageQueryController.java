package com.zhicore.message.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.message.application.dto.MessageVO;
import com.zhicore.message.application.service.MessageQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息读控制器。
 */
@Tag(name = "消息读接口", description = "私信历史、未读数等查询接口")
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Validated
public class MessageQueryController {

    private final MessageQueryService messageQueryService;

    @Operation(summary = "查询消息历史", description = "分页查询指定会话的历史消息记录")
    @GetMapping("/conversation/{conversationId}")
    public ApiResponse<List<MessageVO>> getMessageHistory(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "会话ID必须为正数") Long conversationId,
            @Parameter(description = "游标，用于分页查询", example = "100")
            @RequestParam(required = false) @Min(value = 1, message = "游标必须为正数") Long cursor,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须为正数") int limit) {
        UserContext.requireUserId();
        List<MessageVO> messages = messageQueryService.getMessageHistory(conversationId, cursor, limit);
        return ApiResponse.success(messages);
    }

    @Operation(summary = "获取未读消息数", description = "获取当前用户的未读消息总数")
    @GetMapping("/unread-count")
    public ApiResponse<Integer> getUnreadCount() {
        UserContext.requireUserId();
        int count = messageQueryService.getUnreadCount();
        return ApiResponse.success(count);
    }
}
