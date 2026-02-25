package com.zhicore.api.event.post;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 文章删除事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostDeletedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 作者ID
     */
    private final Long authorId;

    public PostDeletedEvent(Long postId, Long authorId) {
        super();
        this.postId = postId;
        this.authorId = authorId;
    }

    @Override
    public String getTag() {
        return "deleted";
    }
}
