package com.zhicore.content.interfaces.controller.admin;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.dto.admin.outbox.OutboxDeadPageResponse;
import com.zhicore.content.application.service.query.OutboxAdminQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outbox 管理端查询接口。
 */
@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminQueryController {

    private final OutboxAdminQueryService outboxAdminQueryService;

    @GetMapping({"/dead", "/failed"})
    public ApiResponse<OutboxDeadPageResponse> listDead(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String eventType
    ) {
        UserContext.requireUserId();
        return ApiResponse.success(outboxAdminQueryService.listDead(page, size, eventType));
    }
}
