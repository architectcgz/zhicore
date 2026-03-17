package com.zhicore.content.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.TopicId;
import com.zhicore.content.domain.model.UserId;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;

/**
 * 文章创建领域事件
 * 
 * 在 zhicore-content 限界上下文内使用
 * 由 Post 聚合根内部产生
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostCreatedDomainEvent implements DomainEvent<PostId> {
    
    private final String eventId;
    private final Instant occurredAt;
    private final PostId postId;
    private final String title;
    private final String excerpt;          // 移除 content，减少载荷
    private final UserId authorId;
    private final String authorName;
    private final Set<TagId> tagIds;
    private final TopicId topicId;
    private final String topicName;
    private final String status;
    private final Instant publishedAt;
    private final Instant createdAt;
    private final Long aggregateVersion;
    private final Integer schemaVersion;
    
    /**
     * 构造函数（由 DomainEventFactory 或聚合根调用）
     * 
     * @param eventId 事件ID
     * @param occurredAt 事件发生时间
     * @param postId 文章ID
     * @param title 标题
     * @param excerpt 摘要
     * @param authorId 作者ID
     * @param authorName 作者名称
     * @param tagIds 标签ID集合
     * @param topicId 话题ID
     * @param topicName 话题名称
     * @param status 状态
     * @param publishedAt 发布时间
     * @param createdAt 创建时间
     * @param aggregateVersion 聚合根版本号
     */
    @JsonCreator
    public PostCreatedDomainEvent(@JsonProperty("eventId") String eventId,
                                 @JsonProperty("occurredAt") Instant occurredAt,
                                 @JsonProperty("postId") PostId postId,
                                 @JsonProperty("title") String title,
                                 @JsonProperty("excerpt") String excerpt,
                                 @JsonProperty("authorId") UserId authorId,
                                 @JsonProperty("authorName") String authorName,
                                 @JsonProperty("tagIds") Set<TagId> tagIds,
                                 @JsonProperty("topicId") TopicId topicId,
                                 @JsonProperty("topicName") String topicName,
                                 @JsonProperty("status") String status,
                                 @JsonProperty("publishedAt") Instant publishedAt,
                                 @JsonProperty("createdAt") Instant createdAt,
                                 @JsonProperty("aggregateVersion") Long aggregateVersion) {
        this.eventId = eventId;
        this.occurredAt = occurredAt;
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
        this.aggregateVersion = aggregateVersion;
        this.schemaVersion = 1;  // 当前Schema版本
    }
    
    @Override
    public PostId getAggregateId() {
        return postId;
    }
}
