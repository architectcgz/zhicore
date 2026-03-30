package com.zhicore.notification.interfaces.controller;

import com.zhicore.common.constant.CommonConstants;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.notification.application.dto.NotificationDeliveryDTO;
import com.zhicore.notification.application.service.delivery.NotificationDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "通知投递账本", description = "查询与重试通知投递记录")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationDeliveryController {

    private final NotificationDeliveryService notificationDeliveryService;

    @Operation(summary = "查询 delivery 列表")
    @GetMapping("/deliveries")
    public ApiResponse<PageResult<NotificationDeliveryDTO>> getDeliveries(
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Long recipientId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @Parameter(description = "页码，从0开始")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "每页数量")
            @RequestParam(defaultValue = "20") @Min(1) @Max(CommonConstants.MAX_PAGE_SIZE) int size) {
        Long userId = UserContext.requireUserId();
        Long scopedRecipientId = UserContext.isAdmin() ? recipientId : userId;
        return ApiResponse.success(notificationDeliveryService.queryDeliveries(
                campaignId,
                scopedRecipientId,
                channel,
                status,
                page,
                size
        ));
    }

    @Operation(summary = "重试 websocket delivery")
    @PostMapping("/deliveries/{deliveryId}/retry")
    public ApiResponse<Void> retryDelivery(@PathVariable @Min(1) Long deliveryId) {
        Long userId = UserContext.requireUserId();
        notificationDeliveryService.retryDelivery(deliveryId, userId, UserContext.isAdmin());
        return ApiResponse.success();
    }
}
