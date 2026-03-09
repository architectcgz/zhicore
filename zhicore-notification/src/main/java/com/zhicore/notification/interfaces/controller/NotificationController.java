package com.zhicore.notification.interfaces.controller;

import com.zhicore.common.constant.CommonConstants;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.service.NotificationAggregationService;
import com.zhicore.notification.application.service.NotificationApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 通知控制器
 *
 * @author ZhiCore Team
 */
@Tag(name = "通知管理", description = "系统通知、消息推送、已读状态管理等相关接口")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationApplicationService notificationApplicationService;
    private final NotificationAggregationService notificationAggregationService;

    /**
     * 获取聚合通知列表
     * 
     * 按 (type, target_type, target_id) 聚合，返回聚合后的通知
     * 例如："张三等5人赞了你的文章"
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 聚合通知分页结果
     */
    @Operation(summary = "获取聚合通知列表", description = "分页获取当前用户的聚合通知列表，相同类型的通知会被聚合显示")
    @GetMapping
    public ApiResponse<PageResult<AggregatedNotificationVO>> getNotifications(
            @Parameter(description = "页码，从0开始", example = "0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "页码不能为负数") int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "每页数量必须为正数")
            @Max(value = CommonConstants.MAX_PAGE_SIZE, message = "每页数量不能大于100") int size) {
        Long userId = UserContext.requireUserId();
        PageResult<AggregatedNotificationVO> result = 
                notificationAggregationService.getAggregatedNotifications(userId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * 获取未读通知数量
     *
     * @return 未读数量
     */
    @Operation(summary = "获取未读通知数量", description = "获取当前用户的未读通知总数")
    @GetMapping("/unread-count")
    public ApiResponse<Integer> getUnreadCount() {
        Long userId = UserContext.requireUserId();
        int count = notificationApplicationService.getUnreadCount(userId);
        return ApiResponse.success(count);
    }

    /**
     * 标记单条通知为已读
     *
     * @param notificationId 通知ID
     * @return 操作结果
     */
    @Operation(summary = "标记单条通知为已读", description = "将指定的通知标记为已读状态")
    @PostMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
            @Parameter(description = "通知ID", required = true, example = "1")
            @PathVariable @Min(value = 1, message = "通知ID必须为正数") Long notificationId) {
        Long userId = UserContext.requireUserId();
        notificationApplicationService.markAsRead(notificationId, userId);
        return ApiResponse.success();
    }

    /**
     * 批量标记所有通知为已读
     *
     * @return 操作结果
     */
    @Operation(summary = "标记所有通知为已读", description = "将当前用户的所有未读通知标记为已读")
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllAsRead() {
        Long userId = UserContext.requireUserId();
        notificationApplicationService.markAllAsRead(userId);
        return ApiResponse.success();
    }
}
