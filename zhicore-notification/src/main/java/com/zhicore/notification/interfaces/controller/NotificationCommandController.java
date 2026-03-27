package com.zhicore.notification.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知写控制器。
 */
@Tag(name = "通知写接口", description = "通知已读状态写操作")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationCommandController {

    private final NotificationCommandService notificationCommandService;

    @Operation(summary = "标记单条通知为已读", description = "将指定的通知标记为已读状态")
    @PostMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
            @Parameter(description = "通知ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "通知ID必须为正数") Long notificationId) {
        Long userId = UserContext.requireUserId();
        notificationCommandService.markAsRead(notificationId, userId);
        return ApiResponse.success();
    }

    @Operation(summary = "标记所有通知为已读", description = "将当前用户的所有未读通知标记为已读")
    @PostMapping({"/read-all", "/mark-all-read"})
    public ApiResponse<Void> markAllAsRead() {
        Long userId = UserContext.requireUserId();
        notificationCommandService.markAllAsRead(userId);
        return ApiResponse.success();
    }
}
