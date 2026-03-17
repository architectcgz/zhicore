package com.zhicore.content.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.content.domain.model.PostId;
import lombok.Getter;

import java.time.Instant;

/**
 * 文章元数据更新事件
 * 
 * 当文章的标题、摘要或封面图更新时发布此事件。
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostMetadataUpdatedEvent implements DomainEvent<PostId> {
    
    private final String eventId;
    private final Instant occurredAt;
    private final PostId postId;
    private final String title;
    private final String excerpt;
    private final String coverImageId;
    private final Long aggregateVersion;
    private final Integer schemaVersion;
    
    @JsonCreator
    public PostMetadataUpdatedEvent(@JsonProperty("eventId") String eventId,
                                   @JsonProperty("occurredAt") Instant occurredAt,
                                   @JsonProperty("postId") PostId postId,
                                   @JsonProperty("title") String title,
                                   @JsonProperty("excerpt") String excerpt,
                                   @JsonProperty("coverImageId") String coverImageId,
                                   @JsonProperty("aggregateVersion") Long aggregateVersion) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.postId = postId;
        this.title = title;
        this.excerpt = excerpt;
        this.coverImageId = coverImageId;
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
