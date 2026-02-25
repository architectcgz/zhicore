package com.zhicore.content.domain.event;

import com.zhicore.content.domain.model.PostId;
import lombok.Getter;

import java.time.Instant;

/**
 * 文章恢复事件
 * 
 * 当已删除的文章被恢复时发布此事件。
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostRestoredEvent implements DomainEvent<PostId> {
    
    private final String eventId;
    private final Instant occurredAt;
    private final PostId postId;
    private final Long aggregateVersion;
    private final Integer schemaVersion;
    
    public PostRestoredEvent(String eventId, Instant occurredAt, PostId postId, Long aggregateVersion) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.postId = postId;
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
