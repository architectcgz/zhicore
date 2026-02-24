package com.zhicore.integration.messaging.post;

import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PostUnlikedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;
    private final Long userId;
    private final Long authorId;

    public PostUnlikedIntegrationEvent(String eventId, Instant occurredAt, Long aggregateVersion,
                                       Long postId, Long userId, Long authorId) {
        super(eventId, occurredAt, aggregateVersion, 1);
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
    }

    @Override
    public String getTag() {
        return "unliked";
    }

    @Override
    public Long getAggregateId() {
        return postId;
    }
}
