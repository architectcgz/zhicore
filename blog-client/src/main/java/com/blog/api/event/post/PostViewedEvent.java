package com.blog.api.event.post;

import com.blog.api.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 文章浏览事件
 *
 * @author Blog Team
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

    public PostViewedEvent(Long postId, Long userId, Long authorId, LocalDateTime publishedAt) {
        super();
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
        this.publishedAt = publishedAt;
    }

    @Override
    public String getTag() {
        return "viewed";
    }
}
