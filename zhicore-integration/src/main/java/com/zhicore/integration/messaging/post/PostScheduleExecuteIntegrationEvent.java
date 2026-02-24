package com.zhicore.integration.messaging.post;

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

    public PostScheduleExecuteIntegrationEvent(String eventId, Instant occurredAt, Long aggregateVersion,
                                               Long postId, Long authorId, Instant scheduledAt, Integer delayLevel) {
        super(eventId, occurredAt, aggregateVersion, 1);
        this.postId = postId;
        this.authorId = authorId;
        this.scheduledAt = scheduledAt;
        this.delayLevel = delayLevel;
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
