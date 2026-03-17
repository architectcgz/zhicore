package com.zhicore.content.application.dto.admin.outbox;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Outbox 死信事件分页响应（管理端）
 */
@Data
@Builder
public class OutboxDeadPageResponse {

    private int page;
    private int size;
    private long total;
    private List<OutboxDeadEventItem> items;
}
