package com.zhicore.api.event.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;

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
    private final LocalDateTime scheduledAt;

    @JsonCreator
    public PostScheduledEvent(@JsonProperty("eventId") String eventId,
                              @JsonProperty("occurredAt") LocalDateTime occurredAt,
                              @JsonProperty("postId") Long postId,
                              @JsonProperty("authorId") Long authorId,
                              @JsonProperty("scheduledAt") LocalDateTime scheduledAt) {
        super(eventId, occurredAt);
        this.postId = postId;
        this.authorId = authorId;
        this.scheduledAt = scheduledAt;
    }

    public PostScheduledEvent(Long postId, Long authorId, LocalDateTime scheduledAt) {
        this(null, null, postId, authorId, scheduledAt);
    }

    @Override
    public String getTag() {
        return "scheduled";
    }
}
