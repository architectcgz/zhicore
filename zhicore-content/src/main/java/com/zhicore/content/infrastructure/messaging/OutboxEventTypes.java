package com.zhicore.content.infrastructure.messaging;

/**
 * Outbox 事件类型常量。
 *
 * <p>注意：outbox_event.event_type 字段在现有实现中通常存放 IntegrationEvent 的类名，
 * 但也允许存放少量“非 IntegrationEvent”类型的特殊事件（例如 DLQ 事件），由
 * {@link com.zhicore.content.infrastructure.messaging.OutboxEventDispatcher} 做特殊分发。
 */
public final class OutboxEventTypes {

    private OutboxEventTypes() {
    }

    /**
     * 定时发布失败 DLQ 事件（由业务侧写入 Outbox，投递器路由到 RocketMQ DLQ topic）。
     */
    public static final String SCHEDULED_PUBLISH_DLQ = "SCHEDULED_PUBLISH_DLQ";
}

