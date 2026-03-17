package com.zhicore.integration.messaging.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.integration.messaging.DelayableIntegrationEvent;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class AuthorInfoCompensationIntegrationEvent extends IntegrationEvent implements DelayableIntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;
    private final Long userId;
    private final Integer delayLevel;

    @JsonCreator
    public AuthorInfoCompensationIntegrationEvent(@JsonProperty("eventId") String eventId,
                                                  @JsonProperty("occurredAt") Instant occurredAt,
                                                  @JsonProperty("aggregateVersion") Long aggregateVersion,
                                                  @JsonProperty("postId") Long postId,
                                                  @JsonProperty("userId") Long userId,
                                                  @JsonProperty("delayLevel") Integer delayLevel) {
        super(eventId, occurredAt, aggregateVersion, 1);
        this.postId = postId;
        this.userId = userId;
        this.delayLevel = delayLevel;
    }

    @Override
    public String getTag() {
        return "author-info-compensation";
    }

    @Override
    public Long getAggregateId() {
        return postId;
    }
}
