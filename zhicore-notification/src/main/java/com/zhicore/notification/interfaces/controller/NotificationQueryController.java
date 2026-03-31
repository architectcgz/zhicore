package com.zhicore.notification.interfaces.controller;

import com.zhicore.common.constant.CommonConstants;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.service.NotificationAggregationService;
import com.zhicore.notification.application.service.query.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知读控制器。
 */
@Tag(name = "通知查询", description = "通知列表与未读数查询接口")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationQueryController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationAggregationService notificationAggregationService;

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

    @Operation(summary = "获取未读通知数量", description = "获取当前用户的未读通知总数")
    @GetMapping({"/unread/count", "/unread-count"})
    public ApiResponse<Integer> getUnreadCount() {
        Long userId = UserContext.requireUserId();
        int count = notificationQueryService.getUnreadCount(userId);
        return ApiResponse.success(count);
    }

    @Operation(summary = "获取未读分类统计", description = "获取当前用户未读总数和分类维度统计")
    @GetMapping("/unread-breakdown")
    public ApiResponse<NotificationQueryService.UnreadBreakdown> getUnreadBreakdown() {
        Long userId = UserContext.requireUserId();
        NotificationQueryService.UnreadBreakdown breakdown = notificationQueryService.getUnreadBreakdown(userId);
        return ApiResponse.success(breakdown);
    }
}
