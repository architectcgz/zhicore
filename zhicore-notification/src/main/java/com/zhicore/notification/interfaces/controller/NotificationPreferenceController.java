package com.zhicore.notification.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.notification.application.dto.NotificationUserDndDTO;
import com.zhicore.notification.application.dto.NotificationUserPreferenceDTO;
import com.zhicore.notification.application.service.preference.NotificationPreferenceService;
import com.zhicore.notification.interfaces.dto.request.UpdateNotificationDndRequest;
import com.zhicore.notification.interfaces.dto.request.UpdateNotificationPreferenceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知偏好与免打扰控制器。
 */
@Tag(name = "通知偏好", description = "通知偏好与免打扰配置接口")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    @Operation(summary = "获取通知偏好", description = "获取当前用户互动通知与系统通知开关")
    @GetMapping("/preferences")
    public ApiResponse<NotificationUserPreferenceDTO> getPreference() {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(notificationPreferenceService.getPreference(userId));
    }

    @Operation(summary = "更新通知偏好", description = "更新当前用户互动通知与系统通知开关")
    @PutMapping("/preferences")
    public ApiResponse<NotificationUserPreferenceDTO> updatePreference(
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(notificationPreferenceService.updatePreference(userId, request));
    }

    @Operation(summary = "获取免打扰配置", description = "获取当前用户通知免打扰时间段配置")
    @GetMapping("/dnd")
    public ApiResponse<NotificationUserDndDTO> getDnd() {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(notificationPreferenceService.getDnd(userId));
    }

    @Operation(summary = "更新免打扰配置", description = "更新当前用户通知免打扰时间段配置")
    @PutMapping("/dnd")
    public ApiResponse<NotificationUserDndDTO> updateDnd(
            @Valid @RequestBody UpdateNotificationDndRequest request) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(notificationPreferenceService.updateDnd(userId, request));
    }
}
