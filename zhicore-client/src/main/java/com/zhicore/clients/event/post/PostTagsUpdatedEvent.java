package com.zhicore.api.event.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public PostTagsUpdatedEvent(@JsonProperty("eventId") String eventId,
                                @JsonProperty("occurredAt") java.time.Instant occurredAt,
                                @JsonProperty("postId") Long postId,
                                @JsonProperty("oldTagIds") List<Long> oldTagIds,
                                @JsonProperty("newTagIds") List<Long> newTagIds) {
        super(eventId, occurredAt);
        this.postId = postId;
        this.oldTagIds = oldTagIds;
        this.newTagIds = newTagIds;
    }

    public PostTagsUpdatedEvent(Long postId, List<Long> oldTagIds, List<Long> newTagIds) {
        this(null, null, postId, oldTagIds, newTagIds);
    }

    @Override
    public String getTag() {
        return "tags-updated";
    }
}
