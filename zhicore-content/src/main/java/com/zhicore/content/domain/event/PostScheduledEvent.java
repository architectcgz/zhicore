package com.zhicore.content.domain.event;

import com.zhicore.content.domain.model.PostId;
import lombok.Getter;

import java.time.Instant;

/**
 * 文章定时发布事件
 * 
 * 当文章设置定时发布时发布此事件。
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostScheduledEvent implements DomainEvent<PostId> {
    
    private final String eventId;
    private final Instant occurredAt;
    private final PostId postId;
    private final Instant scheduledAt;
    private final Long aggregateVersion;
    private final Integer schemaVersion;
    
    public PostScheduledEvent(String eventId, Instant occurredAt, PostId postId, 
                             Instant scheduledAt, Long aggregateVersion) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.postId = postId;
        this.scheduledAt = scheduledAt;
        this.aggregateVersion = aggregateVersion;
        this.schemaVersion = 1;  // 当前Schema版本
    }
    
    @Override
    public PostId getAggregateId() {
        return postId;
    }
    
    @Override
    public Long getAggregateVersion() {
        return aggregateVersion;
    }
    
    @Override
    public Integer getSchemaVersion() {
        return schemaVersion;
    }
}
