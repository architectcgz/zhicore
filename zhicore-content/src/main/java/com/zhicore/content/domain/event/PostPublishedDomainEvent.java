package com.zhicore.content.domain.event;

import com.zhicore.content.domain.model.PostId;
import lombok.Getter;

import java.time.Instant;

/**
 * 文章发布领域事件
 * 
 * 在 zhicore-content 限界上下文内使用
 * 由 Post 聚合根内部产生
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostPublishedDomainEvent implements DomainEvent<PostId> {
    
    private final String eventId;
    private final Instant occurredAt;
    private final PostId postId;
    private final Instant publishedAt;
    private final Long aggregateVersion;
    private final Integer schemaVersion;
    
    /**
     * 构造函数（由 DomainEventFactory 或聚合根调用）
     * 
     * @param eventId 事件ID
     * @param occurredAt 事件发生时间
     * @param postId 文章ID
     * @param publishedAt 发布时间
     * @param aggregateVersion 聚合根版本号
     */
    public PostPublishedDomainEvent(String eventId, Instant occurredAt,
                                   PostId postId, Instant publishedAt,
                                   Long aggregateVersion) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.postId = postId;
        this.publishedAt = publishedAt;
        this.aggregateVersion = aggregateVersion;
        this.schemaVersion = 1;  // 当前Schema版本
    }
    
    @Override
    public PostId getAggregateId() {
        return postId;
    }
}
