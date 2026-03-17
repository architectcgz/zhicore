package com.zhicore.integration.messaging.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PostLikedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;
    private final Long userId;
    private final Long authorId;
    private final Instant publishedAt;

    @JsonCreator
    public PostLikedIntegrationEvent(@JsonProperty("eventId") String eventId,
                                     @JsonProperty("occurredAt") Instant occurredAt,
                                     @JsonProperty("aggregateVersion") Long aggregateVersion,
                                     @JsonProperty("postId") Long postId,
                                     @JsonProperty("userId") Long userId,
                                     @JsonProperty("authorId") Long authorId,
                                     @JsonProperty("publishedAt") Instant publishedAt) {
        super(eventId, occurredAt, aggregateVersion, 1);
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
        this.publishedAt = publishedAt;
    }

    @Override
    public String getTag() {
        return "liked";
    }

    @Override
    public Long getAggregateId() {
        return postId;
    }
}
