package com.zhicore.api.event.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 文章浏览事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostViewedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 浏览用户ID（可为空，表示匿名用户）
     */
    private final Long userId;

    /**
     * 文章作者ID
     */
    private final Long authorId;

    /**
     * 文章发布时间（用于热度计算时间衰减）
     */
    private final LocalDateTime publishedAt;

    /**
     * 客户端 IP（用于匿名用户浏览去重）
     */
    private final String clientIp;

    /**
     * User-Agent（用于匿名用户浏览去重指纹）
     */
    private final String userAgent;

    @JsonCreator
    public PostViewedEvent(@JsonProperty("eventId") String eventId,
                           @JsonProperty("occurredAt") LocalDateTime occurredAt,
                           @JsonProperty("postId") Long postId,
                           @JsonProperty("userId") Long userId,
                           @JsonProperty("authorId") Long authorId,
                           @JsonProperty("publishedAt") LocalDateTime publishedAt,
                           @JsonProperty("clientIp") String clientIp,
                           @JsonProperty("userAgent") String userAgent) {
        super(eventId, occurredAt);
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
        this.publishedAt = publishedAt;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
    }

    public PostViewedEvent(Long postId, Long userId, Long authorId,
                           LocalDateTime publishedAt, String clientIp, String userAgent) {
        this(null, null, postId, userId, authorId, publishedAt, clientIp, userAgent);
    }

    @Override
    public String getTag() {
        return "viewed";
    }
}
