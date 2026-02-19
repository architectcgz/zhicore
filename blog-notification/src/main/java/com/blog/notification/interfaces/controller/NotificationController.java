package com.blog.notification.interfaces.controller;

import com.blog.common.context.UserContext;
import com.blog.common.result.ApiResponse;
import com.blog.common.result.PageResult;
import com.blog.notification.application.dto.AggregatedNotificationVO;
import com.blog.notification.application.service.NotificationAggregationService;
import com.blog.notification.application.service.NotificationApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 通知控制器
 *
 * @author Blog Team
 */
@Tag(name = "通知管理", description = "系统通知、消息推送、已读状态管理等相关接口")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
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
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
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
        Long userId = UserContext.getUserId();
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
            @PathVariable Long notificationId) {
        Long userId = UserContext.getUserId();
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
        Long userId = UserContext.getUserId();
        notificationApplicationService.markAllAsRead(userId);
        return ApiResponse.success();
    }
}
