package com.zhicore.user.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outbox 事件实体
 * 
 * 用于 Transactional Outbox 模式，确保事件可靠投递到 RocketMQ
 * 
 * <h3>工作原理</h3>
 * <ol>
 *   <li>在业务事务中同时写入业务表和 outbox_events 表，保证原子性</li>
 *   <li>后台定时任务扫描 PENDING 状态的事件并发送到 RocketMQ</li>
 *   <li>发送成功后更新状态为 SENT，失败则递增重试次数</li>
 *   <li>超过最大重试次数后标记为 FAILED，需要人工介入</li>
 * </ol>
 * 
 * <h3>优势</h3>
 * <ul>
 *   <li>MQ 不可用不影响业务操作（如用户资料更新）</li>
 *   <li>所有事件都有记录，可追溯和重放</li>
 *   <li>失败的事件可以手动重试或自动重试</li>
 *   <li>保证事件至少投递一次（At-Least-Once）</li>
 * </ul>
 * 
 * @author System
 * @since 2026-02-19
 * @see OutboxEventStatus
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    
    /**
     * 事件ID（UUID）
     */
    private String id;
    
    /**
     * RocketMQ Topic
     * 
     * 例如：user-profile-changed
     */
    private String topic;
    
    /**
     * RocketMQ Tag
     * 
     * 用于消息过滤，例如：profile-updated
     */
    private String tag;
    
    /**
     * 分片键（Sharding Key）
     * 
     * 用于 RocketMQ 消息路由，确保同一用户的消息发往同一队列
     * 通常使用 userId 作为 sharding key
     */
    private String shardingKey;
    
    /**
     * 事件负载（JSON 格式）
     * 
     * 包含完整的事件数据，例如 UserProfileUpdatedEvent 的 JSON 序列化结果
     */
    private String payload;
    
    /**
     * 事件状态
     * 
     * @see OutboxEventStatus
     */
    private OutboxEventStatus status;
    
    /**
     * 重试次数
     * 
     * 每次发送失败后递增，用于控制重试策略
     */
    private Integer retryCount;
    
    /**
     * 最大重试次数
     *
     * 默认为 10 次，超过后标记为 DEAD
     */
    private Integer maxRetries;

    /**
     * 下次重试时间（指数退避计算）
     */
    private LocalDateTime nextRetryAt;

    /**
     * 创建时间
     * 
     * 事件写入 outbox_events 表的时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 发送时间
     * 
     * 事件成功发送到 RocketMQ 的时间，PENDING 状态时为 null
     */
    private LocalDateTime sentAt;
    
    /**
     * 错误信息
     * 
     * 发送失败时记录的异常信息，用于排查问题
     */
    private String errorMessage;
    
    /**
     * 创建 Outbox 事件的工厂方法
     *
     * <p>封装默认值（UUID、PENDING 状态、当前时间），减少调用方重复代码</p>
     *
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag
     * @param shardingKey 分片键（通常为 userId）
     * @param payload 事件负载（JSON 格式）
     * @return 新的 OutboxEvent 实例
     */
    public static OutboxEvent of(String topic, String tag, String shardingKey, String payload) {
        return new OutboxEvent(
            UUID.randomUUID().toString(),
            topic, tag, shardingKey, payload,
            OutboxEventStatus.PENDING, LocalDateTime.now()
        );
    }

    /**
     * 创建新的 Outbox 事件（使用默认配置）
     * 
     * @param id 事件ID（UUID）
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag
     * @param shardingKey 分片键（通常为 userId）
     * @param payload 事件负载（JSON 格式）
     * @param status 初始状态（通常为 PENDING）
     * @param createdAt 创建时间
     */
    public OutboxEvent(String id, String topic, String tag, String shardingKey, 
                       String payload, OutboxEventStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.topic = topic;
        this.tag = tag;
        this.shardingKey = shardingKey;
        this.payload = payload;
        this.status = status;
        this.retryCount = 0;
        this.maxRetries = 10;
        this.createdAt = createdAt;
        this.nextRetryAt = createdAt;
        this.sentAt = null;
        this.errorMessage = null;
    }

    /**
     * 计算下次重试时间（指数退避：1s→2s→4s→...最大5min）
     */
    public void scheduleNextRetry() {
        this.retryCount++;
        long delaySeconds = Math.min((long) Math.pow(2, this.retryCount - 1), 300);
        this.nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
    }

    /**
     * 是否超过最大重试次数
     */
    public boolean isExhausted() {
        return this.retryCount >= this.maxRetries;
    }
}
