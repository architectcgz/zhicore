package com.zhicore.api.event.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 文章收藏事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostFavoritedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 收藏用户ID
     */
    private final Long userId;

    /**
     * 文章作者ID
     */
    private final Long authorId;

    @JsonCreator
    public PostFavoritedEvent(@JsonProperty("eventId") String eventId,
                              @JsonProperty("occurredAt") java.time.Instant occurredAt,
                              @JsonProperty("postId") Long postId,
                              @JsonProperty("userId") Long userId,
                              @JsonProperty("authorId") Long authorId) {
        super(eventId, occurredAt);
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
    }

    public PostFavoritedEvent(Long postId, Long userId, Long authorId) {
        this(null, null, postId, userId, authorId);
    }

    @Override
    public String getTag() {
        return "favorited";
    }
}
