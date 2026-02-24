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

    public PostUnfavoritedEvent(Long postId, Long userId) {
        super();
        this.postId = postId;
        this.userId = userId;
    }

    @Override
    public String getTag() {
        return "unfavorited";
    }
}
