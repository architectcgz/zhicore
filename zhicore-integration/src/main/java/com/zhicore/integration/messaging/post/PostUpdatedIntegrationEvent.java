package com.zhicore.integration.messaging.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public PostUpdatedIntegrationEvent(@JsonProperty("eventId") String eventId,
                                       @JsonProperty("occurredAt") Instant occurredAt,
                                       @JsonProperty("aggregateVersion") Long aggregateVersion,
                                       @JsonProperty("postId") Long postId,
                                       @JsonProperty("title") String title,
                                       @JsonProperty("content") String content,
                                       @JsonProperty("excerpt") String excerpt,
                                       @JsonProperty("tags") List<String> tags) {
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
