package com.zhicore.api.event.post;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 文章定时发布事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostScheduledEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;

    /**
     * 作者ID
     */
    private final Long authorId;

    /**
     * 定时发布时间
     */
    private final LocalDateTime scheduledAt;

    public PostScheduledEvent(Long postId, Long authorId, LocalDateTime scheduledAt) {
        super();
        this.postId = postId;
        this.authorId = authorId;
        this.scheduledAt = scheduledAt;
    }

    @Override
    public String getTag() {
        return "scheduled";
    }
}
