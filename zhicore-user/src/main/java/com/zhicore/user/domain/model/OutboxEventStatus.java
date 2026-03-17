package com.zhicore.user.domain.model;

/**
 * Outbox 事件状态枚举
 * 
 * 用于 Transactional Outbox 模式，表示事件的处理状态
 * 
 * @author System
 * @since 2026-02-19
 */
public enum OutboxEventStatus {
    
    /**
     * 待发送
     * 
     * 事件已写入 outbox_events 表，等待 dispatcher claim
     */
    PENDING,

    /**
     * 已 claim，正在处理。
     */
    PROCESSING,
    
    /**
     * 已成功收敛
     * 
     * 事件已成功发送到 RocketMQ，不再参与 claim
     */
    SUCCEEDED,
    
    /**
     * 发送失败
     *
     * 事件发送失败，等待下次重试
     */
    FAILED,

    /**
     * 死信
     *
     * 超过最大重试次数（10次），需要人工介入或通过管理接口手动重投
     */
    DEAD

    ;

    public static OutboxEventStatus fromStorageValue(String value) {
        if ("SENT".equals(value)) {
            return SUCCEEDED;
        }
        return OutboxEventStatus.valueOf(value);
    }
}
