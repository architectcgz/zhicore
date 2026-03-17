package com.zhicore.comment.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 评论服务 outbox 事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    private String id;
    private String topic;
    private String tag;
    private String shardingKey;
    private String payload;
    private OutboxEventStatus status;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private String errorMessage;
    private String claimedBy;
    private LocalDateTime claimedAt;

    public static OutboxEvent of(String topic, String tag, String shardingKey, String payload) {
        return new OutboxEvent(
                UUID.randomUUID().toString(),
                topic,
                tag,
                shardingKey,
                payload,
                OutboxEventStatus.PENDING,
                LocalDateTime.now()
        );
    }

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
        this.nextAttemptAt = createdAt;
        this.createdAt = createdAt;
    }

    public void scheduleNextRetry() {
        this.retryCount++;
        long delaySeconds = Math.min((long) Math.pow(2, this.retryCount - 1), 300);
        this.nextAttemptAt = LocalDateTime.now().plusSeconds(delaySeconds);
    }

    public boolean isExhausted() {
        return this.retryCount >= this.maxRetries;
    }

    public void clearClaim() {
        this.claimedBy = null;
        this.claimedAt = null;
    }
}
