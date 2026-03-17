package com.zhicore.integration.messaging.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.integration.messaging.DelayableIntegrationEvent;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PostScheduleExecuteIntegrationEvent extends IntegrationEvent implements DelayableIntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;
    private final Long authorId;
    private final Instant scheduledAt;
    private final Integer delayLevel;
    private final String scheduledPublishEventId;

    @JsonCreator
    public PostScheduleExecuteIntegrationEvent(@JsonProperty("eventId") String eventId,
                                               @JsonProperty("occurredAt") Instant occurredAt,
                                               @JsonProperty("aggregateVersion") Long aggregateVersion,
                                               @JsonProperty("postId") Long postId,
                                               @JsonProperty("authorId") Long authorId,
                                               @JsonProperty("scheduledAt") Instant scheduledAt,
                                               @JsonProperty("delayLevel") Integer delayLevel,
                                               @JsonProperty("scheduledPublishEventId") String scheduledPublishEventId) {
        super(eventId, occurredAt, aggregateVersion, 1);
        this.postId = postId;
        this.authorId = authorId;
        this.scheduledAt = scheduledAt;
        this.delayLevel = delayLevel;
        this.scheduledPublishEventId = scheduledPublishEventId;
    }

    @Override
    public String getTag() {
        return "schedule-execute";
    }

    @Override
    public Long getAggregateId() {
        return postId;
    }
}
