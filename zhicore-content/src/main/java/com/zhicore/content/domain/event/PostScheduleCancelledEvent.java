package com.zhicore.content.domain.event;

import com.zhicore.content.domain.model.PostId;
import lombok.Getter;

import java.time.Instant;

/**
 * 定时发布取消事件
 * 
 * 当文章的定时发布被取消时触发
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostScheduleCancelledEvent implements DomainEvent<PostId> {
    
    private final String eventId;
    private final Instant occurredAt;
    private final PostId postId;
    private final Instant cancelledAt;
    private final Long aggregateVersion;
    private final Integer schemaVersion;
    
    public PostScheduleCancelledEvent(String eventId, Instant occurredAt, PostId postId, Long aggregateVersion) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.postId = postId;
        this.cancelledAt = occurredAt;  // 使用传入的时间
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
