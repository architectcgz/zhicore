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
     * 事件已写入 outbox_events 表，等待后台任务发送到 RocketMQ
     */
    PENDING,
    
    /**
     * 已发送
     * 
     * 事件已成功发送到 RocketMQ
     */
    SENT,
    
    /**
     * 发送失败
     * 
     * 事件发送失败，且已超过最大重试次数
     * 需要人工介入处理
     */
    FAILED
}
