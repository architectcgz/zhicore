package com.zhicore.content.interfaces.dto.admin.outbox;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Outbox 手动重试请求（管理端）
 */
@Data
public class OutboxRetryRequest {

    /**
     * 重试原因（用于审计）
     */
    @NotBlank(message = "reason 不能为空")
    private String reason;
}

