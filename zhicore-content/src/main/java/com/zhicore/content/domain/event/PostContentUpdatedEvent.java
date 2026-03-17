package com.zhicore.content.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.content.domain.model.PostId;
import lombok.Getter;

import java.time.Instant;

/**
 * 文章内容更新事件
 * 
 * 当文章的正文内容更新时发布此事件。
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostContentUpdatedEvent implements DomainEvent<PostId> {
    
    private final String eventId;
    private final Instant occurredAt;
    private final PostId postId;
    private final String content;
    private final String contentType;
    private final Long aggregateVersion;
    private final Integer schemaVersion;
    
    @JsonCreator
    public PostContentUpdatedEvent(@JsonProperty("eventId") String eventId,
                                  @JsonProperty("occurredAt") Instant occurredAt,
                                  @JsonProperty("postId") PostId postId,
                                  @JsonProperty("content") String content,
                                  @JsonProperty("contentType") String contentType,
                                  @JsonProperty("aggregateVersion") Long aggregateVersion) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.postId = postId;
        this.content = content;
        this.contentType = contentType;
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

