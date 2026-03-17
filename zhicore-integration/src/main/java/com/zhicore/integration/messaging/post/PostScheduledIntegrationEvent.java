package com.zhicore.integration.messaging.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PostScheduledIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;
    private final Long authorId;
    private final Instant scheduledAt;

    @JsonCreator
    public PostScheduledIntegrationEvent(@JsonProperty("eventId") String eventId,
                                         @JsonProperty("occurredAt") Instant occurredAt,
                                         @JsonProperty("aggregateVersion") Long aggregateVersion,
                                         @JsonProperty("postId") Long postId,
                                         @JsonProperty("authorId") Long authorId,
                                         @JsonProperty("scheduledAt") Instant scheduledAt) {
        super(eventId, occurredAt, aggregateVersion, 1);
        this.postId = postId;
        this.authorId = authorId;
        this.scheduledAt = scheduledAt;
    }

    @Override
    public String getTag() {
        return "scheduled";
    }

    @Override
    public Long getAggregateId() {
        return postId;
    }
}
