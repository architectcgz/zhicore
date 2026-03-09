package com.zhicore.content.interfaces.controller.admin;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.service.OutboxAdminApplicationService;
import com.zhicore.content.interfaces.dto.admin.outbox.OutboxFailedPageResponse;
import com.zhicore.content.interfaces.dto.admin.outbox.OutboxRetryRequest;
import com.zhicore.content.interfaces.dto.admin.outbox.OutboxRetryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outbox 管理端接口（R14）
 */
@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminController {

    private final OutboxAdminApplicationService outboxAdminApplicationService;

    @GetMapping("/failed")
    public ApiResponse<OutboxFailedPageResponse> listFailed(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String eventType
    ) {
        UserContext.requireUserId();
        return ApiResponse.success(outboxAdminApplicationService.listFailed(page, size, eventType));
    }

    @PostMapping("/{eventId}/retry")
    public ApiResponse<OutboxRetryResponse> retry(
            @PathVariable String eventId,
            @Valid @RequestBody OutboxRetryRequest request
    ) {
        Long operatorId = UserContext.requireUserId();
        return ApiResponse.success(outboxAdminApplicationService.retryFailed(eventId, operatorId, request.getReason()));
    }
}
