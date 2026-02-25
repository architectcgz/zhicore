package com.zhicore.content.domain.event;

import com.zhicore.content.domain.model.PostId;
import lombok.Getter;

import java.time.Instant;

/**
 * 文章物理删除事件
 * 
 * 当文章被物理删除时发布此事件。
 * 注意：物理删除是不可逆操作。
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostPurgedEvent implements DomainEvent<PostId> {
    
    private final String eventId;
    private final Instant occurredAt;
    private final PostId postId;
    private final Instant purgedAt;
    private final Long aggregateVersion;
    private final Integer schemaVersion;
    
    public PostPurgedEvent(String eventId, Instant occurredAt, PostId postId) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.postId = postId;
        this.purgedAt = occurredAt;  // 使用传入的时间
        this.aggregateVersion = null;  // 物理删除后版本号不再有意义
        this.schemaVersion = 1;  // 当前Schema版本
    }
    
    @Override
    public PostId getAggregateId() {
        return postId;
    }
    
    @Override
    public Long getAggregateVersion() {
        return aggregateVersion;  // 物理删除时返回 null
    }
    
    @Override
    public Integer getSchemaVersion() {
        return schemaVersion;
    }
}
