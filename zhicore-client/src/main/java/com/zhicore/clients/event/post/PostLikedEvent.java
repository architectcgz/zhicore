package com.zhicore.api.event.post;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;

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
    private final LocalDateTime publishedAt;

    public PostLikedEvent(Long postId, Long userId, Long authorId) {
        this(postId, userId, authorId, null);
    }

    public PostLikedEvent(Long postId, Long userId, Long authorId, LocalDateTime publishedAt) {
        super();
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
        this.publishedAt = publishedAt;
    }

    @Override
    public String getTag() {
        return "liked";
    }
}
