package com.zhicore.integration.messaging.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PostFavoritedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;
    private final Long userId;
    private final Long authorId;

    @JsonCreator
    public PostFavoritedIntegrationEvent(@JsonProperty("eventId") String eventId,
                                         @JsonProperty("occurredAt") Instant occurredAt,
                                         @JsonProperty("aggregateVersion") Long aggregateVersion,
                                         @JsonProperty("postId") Long postId,
                                         @JsonProperty("userId") Long userId,
                                         @JsonProperty("authorId") Long authorId) {
        super(eventId, occurredAt, aggregateVersion, 1);
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
    }

    @Override
    public String getTag() {
        return "favorited";
    }

    @Override
    public Long getAggregateId() {
        return postId;
    }
}
