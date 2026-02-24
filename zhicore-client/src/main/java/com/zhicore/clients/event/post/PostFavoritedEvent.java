package com.zhicore.api.event.post;

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

    public PostFavoritedEvent(Long postId, Long userId, Long authorId) {
        super();
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
    }

    @Override
    public String getTag() {
        return "favorited";
    }
}
