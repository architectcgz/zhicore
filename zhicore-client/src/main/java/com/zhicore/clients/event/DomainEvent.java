package com.zhicore.api.event;

import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 领域事件基类
 */
@Getter
public abstract class DomainEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件ID
     */
    private final String eventId;

    /**
     * 事件发生时间
     */
    private final Instant occurredAt;

    protected DomainEvent() {
        this(UUID.randomUUID().toString().replace("-", ""), Instant.now());
    }

    protected DomainEvent(String eventId, Instant occurredAt) {
        this.eventId = Objects.requireNonNullElseGet(
                eventId,
                () -> UUID.randomUUID().toString().replace("-", "")
        );
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
    }

    /**
     * 获取事件标签（用于 RocketMQ Tag）
     */
    public abstract String getTag();
}
