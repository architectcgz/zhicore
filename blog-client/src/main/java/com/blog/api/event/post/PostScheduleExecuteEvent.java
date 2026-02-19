package com.blog.api.event.post;

import com.blog.api.event.DomainEvent;
import lombok.Getter;

/**
 * 文章定时发布执行事件（延迟消息）
 *
 * @author Blog Team
 */
@Getter
public class PostScheduleExecuteEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 作者ID
     */
    private final Long authorId;

    public PostScheduleExecuteEvent(Long postId, Long authorId) {
        super();
        this.postId = postId;
        this.authorId = authorId;
    }

    @Override
    public String getTag() {
        return "schedule-execute";
    }
}
