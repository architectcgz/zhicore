package com.zhicore.integration.messaging.post;

import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * 文章标签更新集成事件
 * 
 * 用于跨服务通信，通过 RocketMQ 传递
 * 只包含跨服务必需的最小信息
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostTagsUpdatedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;              // 原始类型
    private final List<Long> oldTagIds;     // 原始类型列表
    private final List<Long> newTagIds;     // 原始类型列表
    private final Instant updatedAt;

    /**
     * 构造函数
     * 
     * @param eventId 事件ID（从领域事件复制）
     * @param occurredAt 事件发生时间（从领域事件复制）
     * @param postId 文章ID
     * @param oldTagIds 旧标签ID列表
     * @param newTagIds 新标签ID列表
     * @param updatedAt 更新时间
     * @param aggregateVersion 聚合根版本号
     */
    public PostTagsUpdatedIntegrationEvent(String eventId, Instant occurredAt,
                                          Long postId, List<Long> oldTagIds,
                                          List<Long> newTagIds, Instant updatedAt,
                                          Long aggregateVersion) {
        super(eventId, occurredAt, aggregateVersion, 1);  // schemaVersion = 1
        this.postId = postId;
        this.oldTagIds = oldTagIds;
        this.newTagIds = newTagIds;
        this.updatedAt = updatedAt;
    }

    @Override
    public String getTag() {
        return "tags-updated";
    }
    
    @Override
    public Long getAggregateId() {
        return postId;
    }
}
