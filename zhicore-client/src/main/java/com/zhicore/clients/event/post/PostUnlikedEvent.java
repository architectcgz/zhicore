package com.zhicore.api.event.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 文章取消点赞事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostUnlikedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 文章作者ID
     */
    private final Long authorId;

    @JsonCreator
    public PostUnlikedEvent(@JsonProperty("eventId") String eventId,
                            @JsonProperty("occurredAt") java.time.Instant occurredAt,
                            @JsonProperty("postId") Long postId,
                            @JsonProperty("userId") Long userId,
                            @JsonProperty("authorId") Long authorId) {
        super(eventId, occurredAt);
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
    }

    public PostUnlikedEvent(Long postId, Long userId) {
        this(postId, userId, null);
    }

    public PostUnlikedEvent(Long postId, Long userId, Long authorId) {
        this(null, null, postId, userId, authorId);
    }

    @Override
    public String getTag() {
        return "unliked";
    }
}
