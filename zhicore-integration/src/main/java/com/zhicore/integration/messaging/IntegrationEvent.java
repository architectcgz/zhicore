package com.zhicore.integration.messaging;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

/**
 * 集成事件基类
 * 
 * 用于跨服务的消息传递，通过 RocketMQ 发布和订阅。
 * 注意：不实现领域层的 DomainEvent 接口，明确分离领域事件和集成事件。
 * 
 * @author ZhiCore Team
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class IntegrationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件ID（唯一标识，与领域事件保持一致）
     */
    private final String eventId;

    /**
     * 事件发生时间（UTC时间戳，与领域事件保持一致）
     */
    private final Instant occurredAt;
    
    /**
     * 聚合根版本号（用于并发控制和事件顺序）
     */
    private final Long aggregateVersion;
    
    /**
     * 事件Schema版本（用于消息演进和向后兼容）
     */
    private final Integer schemaVersion;

    /**
     * 构造函数
     * 
     * @param eventId 事件ID（从领域事件复制）
     * @param occurredAt 事件发生时间（从领域事件复制）
     * @param aggregateVersion 聚合根版本号（用于并发控制）
     * @param schemaVersion 事件Schema版本（用于消息演进）
     */
    protected IntegrationEvent(String eventId, Instant occurredAt, 
                              Long aggregateVersion, Integer schemaVersion) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.aggregateVersion = aggregateVersion;
        this.schemaVersion = schemaVersion;
    }

    /**
     * 获取事件标签（用于 RocketMQ Tag）
     */
    @JsonIgnore
    public abstract String getTag();
    
    /**
     * 获取聚合根ID（原始类型，用于跨服务传递）
     */
    @JsonIgnore
    public abstract Long getAggregateId();
}
