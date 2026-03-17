package com.zhicore.content.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;

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
    private final Set<TagId> tagIds;
    private final Instant purgedAt;
    private final Long aggregateVersion;
    private final Integer schemaVersion;
    
    @JsonCreator
    public PostPurgedEvent(@JsonProperty("eventId") String eventId,
                           @JsonProperty("occurredAt") Instant occurredAt,
                           @JsonProperty("postId") PostId postId,
                           @JsonProperty("tagIds") Set<TagId> tagIds,
                           @JsonProperty("aggregateVersion") Long aggregateVersion) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.postId = postId;
        this.tagIds = tagIds;
        this.purgedAt = occurredAt;  // 使用传入的时间
        this.aggregateVersion = aggregateVersion;
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
