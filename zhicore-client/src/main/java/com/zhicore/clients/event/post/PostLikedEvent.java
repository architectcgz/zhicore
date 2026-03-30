package com.zhicore.api.event.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 文章点赞事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostLikedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 点赞用户ID
     */
    private final Long userId;

    /**
     * 文章作者ID（用于发送通知）
     */
    private final Long authorId;

    /**
     * 文章发布时间（用于热度计算时间衰减）
     */
    private final OffsetDateTime publishedAt;

    @JsonCreator
    public PostLikedEvent(@JsonProperty("eventId") String eventId,
                          @JsonProperty("occurredAt") java.time.Instant occurredAt,
                          @JsonProperty("postId") Long postId,
                          @JsonProperty("userId") Long userId,
                          @JsonProperty("authorId") Long authorId,
                          @JsonProperty("publishedAt") OffsetDateTime publishedAt) {
        super(eventId, occurredAt);
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
        this.publishedAt = publishedAt;
    }

    public PostLikedEvent(Long postId, Long userId, Long authorId) {
        this(postId, userId, authorId, null);
    }

    public PostLikedEvent(Long postId, Long userId, Long authorId, OffsetDateTime publishedAt) {
        this(null, null, postId, userId, authorId, publishedAt);
    }

    @Override
    public String getTag() {
        return "liked";
    }
}
