package com.zhicore.api.event.comment;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

/**
 * 评论删除事件。
 */
@Getter
public class CommentDeletedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    private final Long commentId;
    private final Long postId;
    private final Long authorId;

    public CommentDeletedEvent(Long commentId, Long postId, Long authorId) {
        super();
        this.commentId = commentId;
        this.postId = postId;
        this.authorId = authorId;
    }

    @Override
    public String getTag() {
        return "deleted";
    }
}
