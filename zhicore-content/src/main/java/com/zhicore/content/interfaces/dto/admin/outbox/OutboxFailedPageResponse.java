package com.zhicore.content.interfaces.dto.admin.outbox;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Outbox 失败事件分页响应（管理端）
 */
@Data
@Builder
public class OutboxFailedPageResponse {

    private int page;
    private int size;
    private long total;
    private List<OutboxFailedEventItem> items;
}

