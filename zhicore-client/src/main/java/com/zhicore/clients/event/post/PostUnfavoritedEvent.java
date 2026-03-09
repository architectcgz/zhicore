package com.zhicore.api.event.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 文章取消收藏事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostUnfavoritedEvent extends DomainEvent {

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
     * 文章作者ID（用于对称扣减创作者热度）
     */
    private final Long authorId;

    @JsonCreator
    public PostUnfavoritedEvent(@JsonProperty("eventId") String eventId,
                                @JsonProperty("occurredAt") java.time.LocalDateTime occurredAt,
                                @JsonProperty("postId") Long postId,
                                @JsonProperty("userId") Long userId,
                                @JsonProperty("authorId") Long authorId) {
        super(eventId, occurredAt);
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
    }

    public PostUnfavoritedEvent(Long postId, Long userId, Long authorId) {
        this(null, null, postId, userId, authorId);
    }

    @Override
    public String getTag() {
        return "unfavorited";
    }
}
