package com.blog.api.event.post;

import com.blog.api.event.DomainEvent;
import lombok.Getter;

/**
 * 文章取消点赞事件
 *
 * @author Blog Team
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

    public PostUnlikedEvent(Long postId, Long userId) {
        this(postId, userId, null);
    }

    public PostUnlikedEvent(Long postId, Long userId, Long authorId) {
        super();
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
    }

    @Override
    public String getTag() {
        return "unliked";
    }
}
