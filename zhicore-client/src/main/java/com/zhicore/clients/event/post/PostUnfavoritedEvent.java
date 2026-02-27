package com.zhicore.api.event.post;

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

    public PostUnfavoritedEvent(Long postId, Long userId, Long authorId) {
        super();
        this.postId = postId;
        this.userId = userId;
        this.authorId = authorId;
    }

    @Override
    public String getTag() {
        return "unfavorited";
    }
}
