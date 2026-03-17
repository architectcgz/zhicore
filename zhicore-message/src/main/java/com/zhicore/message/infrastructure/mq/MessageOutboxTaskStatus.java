package com.zhicore.message.infrastructure.mq;

/**
 * 消息 Outbox 任务状态。
 */
public enum MessageOutboxTaskStatus {
    PENDING,
    PROCESSING,
    FAILED,
    SUCCEEDED,
    DEAD;

    public static MessageOutboxTaskStatus fromStorageValue(String value) {
        if ("DISPATCHED".equals(value)) {
            return SUCCEEDED;
        }
        return MessageOutboxTaskStatus.valueOf(value);
    }
}
