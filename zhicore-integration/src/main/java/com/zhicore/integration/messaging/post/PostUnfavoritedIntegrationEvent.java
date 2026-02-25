package com.zhicore.integration.messaging.post;

import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PostUnfavoritedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;
    private final Long userId;

    public PostUnfavoritedIntegrationEvent(String eventId, Instant occurredAt, Long aggregateVersion,
                                           Long postId, Long userId) {
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
