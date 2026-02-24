package com.zhicore.content.domain.event;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;

/**
 * 文章标签更新领域事件
 * 
 * 在 zhicore-content 限界上下文内使用
 * 由 Post 聚合根内部产生
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostTagsUpdatedDomainEvent implements DomainEvent<PostId> {
    
    private final String eventId;
    private final Instant occurredAt;
    private final PostId postId;
    private final Set<TagId> oldTagIds;
    private final Set<TagId> newTagIds;
    private final Instant updatedAt;
    private final Long aggregateVersion;
    private final Integer schemaVersion;
    
    /**
     * 构造函数（由 DomainEventFactory 或聚合根调用）
     * 
     * @param eventId 事件ID
     * @param occurredAt 事件发生时间
     * @param postId 文章ID
     * @param oldTagIds 旧标签ID集合
     * @param newTagIds 新标签ID集合
     * @param updatedAt 更新时间
     * @param aggregateVersion 聚合根版本号
     */
    public PostTagsUpdatedDomainEvent(String eventId, Instant occurredAt,
                                     PostId postId, Set<TagId> oldTagIds,
                                     Set<TagId> newTagIds, Instant updatedAt,
                                     Long aggregateVersion) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.postId = postId;
        this.oldTagIds = oldTagIds;
        this.newTagIds = newTagIds;
        this.updatedAt = updatedAt;
        this.aggregateVersion = aggregateVersion;
        this.schemaVersion = 1;  // 当前Schema版本
    }
    
    @Override
    public PostId getAggregateId() {
        return postId;
    }
}
