package com.zhicore.integration.messaging.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PostUnfavoritedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;
    private final Long userId;

    @JsonCreator
    public PostUnfavoritedIntegrationEvent(@JsonProperty("eventId") String eventId,
                                           @JsonProperty("occurredAt") Instant occurredAt,
                                           @JsonProperty("aggregateVersion") Long aggregateVersion,
                                           @JsonProperty("postId") Long postId,
                                           @JsonProperty("userId") Long userId) {
        super(eventId, occurredAt, aggregateVersion, 1);
        this.postId = postId;
        this.userId = userId;
    }

    @Override
    public String getTag() {
        return "unfavorited";
    }

    @Override
    public Long getAggregateId() {
        return postId;
    }
}
