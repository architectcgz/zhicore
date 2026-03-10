package com.zhicore.content.application.dto.admin.outbox;

import lombok.Builder;
import lombok.Data;

/**
 * Outbox 手动重试响应（管理端）
 */
@Data
@Builder
public class OutboxRetryResponse {

    private String eventId;
    private String status;
}
