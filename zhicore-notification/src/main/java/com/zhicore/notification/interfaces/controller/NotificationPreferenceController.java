package com.zhicore.notification.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.notification.application.service.command.NotificationPreferenceCommandService;
import com.zhicore.notification.application.service.query.NotificationPreferenceQueryService;
import com.zhicore.notification.domain.model.AuthorSubscriptionLevel;
import com.zhicore.notification.domain.model.NotificationCategory;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.model.UserNotificationDnd;
import com.zhicore.notification.interfaces.dto.request.UpdateAuthorSubscriptionRequest;
import com.zhicore.notification.interfaces.dto.request.UpdateNotificationDndRequest;
import com.zhicore.notification.interfaces.dto.request.UpdateNotificationPreferenceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;

/**
 * 通知偏好控制器
 */
@Tag(name = "通知偏好", description = "通知偏好、免打扰和作者订阅接口")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class NotificationPreferenceController {

    private final NotificationPreferenceCommandService notificationPreferenceCommandService;
    private final NotificationPreferenceQueryService notificationPreferenceQueryService;

    @Operation(summary = "获取通知偏好")
    @GetMapping("/notification-preferences")
    public ApiResponse<?> getPreferences() {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(notificationPreferenceQueryService.getPreferences(userId));
    }

    @Operation(summary = "更新通知偏好")
    @PutMapping("/notification-preferences")
    public ApiResponse<Void> updatePreference(
            @Valid @NotNull(message = "请求体不能为空") @RequestBody UpdateNotificationPreferenceRequest request) {
        Long userId = UserContext.requireUserId();
        notificationPreferenceCommandService.updateNotificationPreference(
                userId,
                parseNotificationType(request.getNotificationType()),
                NotificationChannel.fromValue(request.getChannel()),
                request.getEnabled()
        );
        return ApiResponse.success();
    }

    @Operation(summary = "获取免打扰配置")
    @GetMapping("/notification-dnd")
    public ApiResponse<UserNotificationDnd> getDnd() {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(notificationPreferenceQueryService.getDnd(userId)
                .orElse(UserNotificationDnd.disabled(userId)));
    }

    @Operation(summary = "更新免打扰配置")
    @PutMapping("/notification-dnd")
    public ApiResponse<Void> updateDnd(
            @Valid @NotNull(message = "请求体不能为空") @RequestBody UpdateNotificationDndRequest request) {
        Long userId = UserContext.requireUserId();
        notificationPreferenceCommandService.updateNotificationDnd(
                userId,
                request.getEnabled(),
                parseLocalTime(request.getStartTime()),
                parseLocalTime(request.getEndTime()),
                parseCategories(request.getCategories()),
                parseChannels(request.getChannels())
        );
        return ApiResponse.success();
    }

    @Operation(summary = "获取作者订阅配置")
    @GetMapping("/notification-authors/{authorId}/subscription")
    public ApiResponse<?> getAuthorSubscription(
            @Parameter(description = "作者ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "作者ID必须为正数") Long authorId) {
        Long userId = UserContext.requireUserId();
        return ApiResponse.success(notificationPreferenceQueryService.getAuthorSubscription(userId, authorId));
    }

    @Operation(summary = "更新作者订阅配置")
    @PutMapping("/notification-authors/{authorId}/subscription")
    public ApiResponse<Void> updateAuthorSubscription(
            @Parameter(description = "作者ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "作者ID必须为正数") Long authorId,
            @Valid @NotNull(message = "请求体不能为空") @RequestBody UpdateAuthorSubscriptionRequest request) {
        Long userId = UserContext.requireUserId();
        notificationPreferenceCommandService.updateAuthorSubscription(
                userId,
                authorId,
                AuthorSubscriptionLevel.fromValue(request.getLevel()),
                request.getInAppEnabled(),
                request.getWebsocketEnabled(),
                request.getEmailEnabled(),
                request.getDigestEnabled()
        );
        return ApiResponse.success();
    }

    private NotificationType parseNotificationType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("通知类型不能为空");
        }
        try {
            return NotificationType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法通知类型: " + value);
        }
    }

    private EnumSet<NotificationCategory> parseCategories(List<String> values) {
        if (values == null || values.isEmpty()) {
            return EnumSet.noneOf(NotificationCategory.class);
        }
        EnumSet<NotificationCategory> result = EnumSet.noneOf(NotificationCategory.class);
        values.forEach(value -> result.add(NotificationCategory.fromValue(value)));
        return result;
    }

    private EnumSet<NotificationChannel> parseChannels(List<String> values) {
        if (values == null || values.isEmpty()) {
            return EnumSet.noneOf(NotificationChannel.class);
        }
        EnumSet<NotificationChannel> result = EnumSet.noneOf(NotificationChannel.class);
        values.forEach(value -> result.add(NotificationChannel.fromValue(value)));
        return result;
    }

    private LocalTime parseLocalTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("非法时间格式: " + value);
        }
    }
}
