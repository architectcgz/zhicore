package com.zhicore.integration.messaging.post;

import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * 文章创建集成事件
 * 
 * 用于跨服务通信，通过 RocketMQ 传递
 * 只包含跨服务必需的最小信息
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostCreatedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    private final Long postId;              // 原始类型
    private final String title;
    private final String excerpt;           // 移除 content，减少载荷
    private final Long authorId;            // 原始类型
    private final String authorName;
    private final List<Long> tagIds;        // 原始类型列表
    private final Long topicId;             // 原始类型
    private final String topicName;
    private final String status;
    private final Instant publishedAt;
    private final Instant createdAt;

    /**
     * 构造函数
     * 
     * @param eventId 事件ID（从领域事件复制）
     * @param occurredAt 事件发生时间（从领域事件复制）
     * @param postId 文章ID
     * @param title 标题
     * @param excerpt 摘要（不包含完整内容）
     * @param authorId 作者ID
     * @param authorName 作者名称
     * @param tagIds 标签ID列表
     * @param topicId 话题ID
     * @param topicName 话题名称
     * @param status 状态
     * @param publishedAt 发布时间
     * @param createdAt 创建时间
     * @param aggregateVersion 聚合根版本号
     */
    public PostCreatedIntegrationEvent(String eventId, Instant occurredAt,
                                      Long postId, String title, String excerpt,
                                      Long authorId, String authorName,
                                      List<Long> tagIds, Long topicId, String topicName,
                                      String status, Instant publishedAt,
                                      Instant createdAt, Long aggregateVersion) {
        super(eventId, occurredAt, aggregateVersion, 1);  // schemaVersion = 1
        this.postId = postId;
        this.title = title;
        this.excerpt = excerpt;
        this.authorId = authorId;
        this.authorName = authorName;
        this.tagIds = tagIds;
        this.topicId = topicId;
        this.topicName = topicName;
        this.status = status;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
    }

    @Override
    public String getTag() {
        return "created";
    }
    
    @Override
    public Long getAggregateId() {
        return postId;
    }
}
