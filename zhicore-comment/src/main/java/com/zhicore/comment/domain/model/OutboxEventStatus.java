package com.zhicore.comment.domain.model;

/**
 * 评论服务 outbox 事件状态。
 */
public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    DEAD;

    public static OutboxEventStatus fromStorageValue(String value) {
        if ("SENT".equals(value)) {
            return SUCCEEDED;
        }
        return OutboxEventStatus.valueOf(value);
    }
}
