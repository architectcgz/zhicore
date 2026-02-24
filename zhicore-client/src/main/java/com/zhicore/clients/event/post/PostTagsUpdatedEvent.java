package com.zhicore.api.event.post;

import com.zhicore.api.event.DomainEvent;
import lombok.Getter;

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
    private final Long postId;

    /**
     * 旧标签ID列表
     */
    private final List<Long> oldTagIds;

    /**
     * 新标签ID列表
     */
    private final List<Long> newTagIds;

    public PostTagsUpdatedEvent(Long postId, List<Long> oldTagIds, List<Long> newTagIds) {
        super();
        this.postId = postId;
        this.oldTagIds = oldTagIds;
        this.newTagIds = newTagIds;
    }

    @Override
    public String getTag() {
        return "tags-updated";
    }
}
