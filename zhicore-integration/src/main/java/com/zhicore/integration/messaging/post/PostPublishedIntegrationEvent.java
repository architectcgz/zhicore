package com.zhicore.integration.messaging.post;

import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

/**
 * 文章发布集成事件
 * 
 * 用于跨服务通信，通过 RocketMQ 传递
 * 只包含跨服务必需的最小信息
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostPublishedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;              // 原始类型
    private final Instant publishedAt;

    /**
     * 构造函数
     * 
     * @param eventId 事件ID（从领域事件复制）
     * @param occurredAt 事件发生时间（从领域事件复制）
     * @param postId 文章ID
     * @param publishedAt 发布时间
     * @param aggregateVersion 聚合根版本号
     */
    public PostPublishedIntegrationEvent(String eventId, Instant occurredAt,
                                        Long postId, Instant publishedAt,
                                        Long aggregateVersion) {
        super(eventId, occurredAt, aggregateVersion, 1);  // schemaVersion = 1
        this.postId = postId;
        this.publishedAt = publishedAt;
    }

    @Override
    public String getTag() {
        return "published";
    }
    
    @Override
    public Long getAggregateId() {
        return postId;
    }
}
