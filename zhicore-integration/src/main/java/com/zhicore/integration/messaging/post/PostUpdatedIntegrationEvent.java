package com.zhicore.integration.messaging.post;

import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
public class PostUpdatedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;
    private final String title;
    private final String content;
    private final String excerpt;
    private final List<String> tags;

    public PostUpdatedIntegrationEvent(String eventId, Instant occurredAt, Long aggregateVersion,
                                       Long postId, String title, String content,
                                       String excerpt, List<String> tags) {
        super(eventId, occurredAt, aggregateVersion, 1);
        this.postId = postId;
        this.title = title;
        this.content = content;
        this.excerpt = excerpt;
        this.tags = tags;
    }

    @Override
    public String getTag() {
        return "updated";
    }

    @Override
    public Long getAggregateId() {
        return postId;
    }
}
