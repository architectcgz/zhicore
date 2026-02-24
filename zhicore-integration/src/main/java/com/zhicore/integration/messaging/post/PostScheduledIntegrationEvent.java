package com.zhicore.integration.messaging.post;

import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PostScheduledIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;
    private final Long authorId;
    private final Instant scheduledAt;

    public PostScheduledIntegrationEvent(String eventId, Instant occurredAt, Long aggregateVersion,
                                         Long postId, Long authorId, Instant scheduledAt) {
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
