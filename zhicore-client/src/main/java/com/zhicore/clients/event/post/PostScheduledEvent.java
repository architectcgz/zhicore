package com.zhicore.api.event.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 文章定时发布事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostScheduledEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 作者ID
     */
    private final Long authorId;

    /**
     * 定时发布时间
     */
    private final OffsetDateTime scheduledAt;

    @JsonCreator
    public PostScheduledEvent(@JsonProperty("eventId") String eventId,
                              @JsonProperty("occurredAt") java.time.Instant occurredAt,
                              @JsonProperty("postId") Long postId,
                              @JsonProperty("authorId") Long authorId,
                              @JsonProperty("scheduledAt") OffsetDateTime scheduledAt) {
        super(eventId, occurredAt);
        this.postId = postId;
        this.authorId = authorId;
        this.scheduledAt = scheduledAt;
    }

    public PostScheduledEvent(Long postId, Long authorId, OffsetDateTime scheduledAt) {
        this(null, null, postId, authorId, scheduledAt);
    }

    @Override
    public String getTag() {
        return "scheduled";
    }
}
