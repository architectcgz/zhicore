package com.zhicore.comment.application.port.event;

import com.zhicore.api.event.comment.CommentCreatedEvent;
import com.zhicore.api.event.comment.CommentDeletedEvent;

/**
 * 评论领域事件发布端口
 */
public interface CommentEventPort {

    /**
     * 发布评论创建事件
     */
    void publishCommentCreated(CommentCreatedEvent event);

    /**
     * 发布评论删除事件
     */
    void publishCommentDeleted(CommentDeletedEvent event);
}
