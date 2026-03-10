package com.zhicore.content.application.model;

/**
 * Outbox 事件类型常量。
 */
public final class OutboxEventTypes {

    private OutboxEventTypes() {
    }

    public static final String SCHEDULED_PUBLISH_DLQ = "SCHEDULED_PUBLISH_DLQ";
}
