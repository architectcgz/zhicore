package com.zhicore.content.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;

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
    private final Set<TagId> tagIds;
    private final Long aggregateVersion;
    private final Integer schemaVersion;
    
    @JsonCreator
    public PostRestoredEvent(@JsonProperty("eventId") String eventId,
                             @JsonProperty("occurredAt") Instant occurredAt,
                             @JsonProperty("postId") PostId postId,
                             @JsonProperty("tagIds") Set<TagId> tagIds,
                             @JsonProperty("aggregateVersion") Long aggregateVersion) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.postId = postId;
        this.tagIds = tagIds;
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
