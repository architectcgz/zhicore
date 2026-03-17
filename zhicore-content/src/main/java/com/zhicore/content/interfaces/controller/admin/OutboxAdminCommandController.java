package com.zhicore.content.interfaces.controller.admin;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.dto.admin.outbox.OutboxRetryResponse;
import com.zhicore.content.application.service.command.OutboxAdminCommandService;
import com.zhicore.content.interfaces.dto.admin.outbox.OutboxRetryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outbox 管理端写接口。
 */
@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminCommandController {

    private final OutboxAdminCommandService outboxAdminCommandService;

    @PostMapping("/{eventId}/retry")
    public ApiResponse<OutboxRetryResponse> retry(
            @PathVariable String eventId,
            @Valid @RequestBody OutboxRetryRequest request
    ) {
        Long operatorId = UserContext.requireUserId();
        return ApiResponse.success(outboxAdminCommandService.retryDead(eventId, operatorId, request.getReason()));
    }
}
