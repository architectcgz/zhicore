package com.zhicore.content.domain.event;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章标签更新事件
 *
 * @author ZhiCore Team
 */
@Getter
public class PostTagsUpdatedEvent extends DomainEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final String postId;

    /**
     * 旧标签ID列表
     */
    private final List<String> oldTagIds;

    /**
     * 新标签ID列表
     */
    private final List<String> newTagIds;

    /**
     * 更新时间
     */
    private final LocalDateTime updatedAt;

    public PostTagsUpdatedEvent(String postId, List<String> oldTagIds, List<String> newTagIds,
                               LocalDateTime updatedAt, LocalDateTime eventTime) {
        super();
        this.postId = postId;
        this.oldTagIds = oldTagIds;
        this.newTagIds = newTagIds;
        this.updatedAt = updatedAt;
    }

    @Override
    public String getTag() {
        return "tags-updated";
    }
}
