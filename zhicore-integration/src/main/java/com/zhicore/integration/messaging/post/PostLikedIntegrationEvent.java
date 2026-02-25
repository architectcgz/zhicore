package com.zhicore.integration.messaging.post;

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

    public PostLikedIntegrationEvent(String eventId, Instant occurredAt, Long aggregateVersion,
                                     Long postId, Long userId, Long authorId, Instant publishedAt) {
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
