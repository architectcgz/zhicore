package com.zhicore.api.event.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 文章定时发布执行事件（延迟消息）
 *
 * @author ZhiCore Team
 */
@Getter
public class PostScheduleExecuteEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 作者ID
     */
    private final Long authorId;

    @JsonCreator
    public PostScheduleExecuteEvent(@JsonProperty("eventId") String eventId,
                                    @JsonProperty("occurredAt") java.time.LocalDateTime occurredAt,
                                    @JsonProperty("postId") Long postId,
                                    @JsonProperty("authorId") Long authorId) {
        super(eventId, occurredAt);
        this.postId = postId;
        this.authorId = authorId;
    }

    public PostScheduleExecuteEvent(Long postId, Long authorId) {
        this(null, null, postId, authorId);
    }

    @Override
    public String getTag() {
        return "schedule-execute";
    }
}
