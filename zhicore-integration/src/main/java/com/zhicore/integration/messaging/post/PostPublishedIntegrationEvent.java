package com.zhicore.integration.messaging.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

/**
 * 文章发布集成事件
 * 
 * 用于跨服务通信，通过 RocketMQ 传递
 * 只包含跨服务必需的最小信息
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostPublishedIntegrationEvent extends IntegrationEvent {
	
    private static final long serialVersionUID = 1L;
	
    private final Long postId;
    private final Long authorId;
    private final String title;
    private final String excerpt;
    private final Instant publishedAt;

    /**
     * 构造函数
     * 
     * @param eventId 事件ID（从领域事件复制）
     * @param occurredAt 事件发生时间（从领域事件复制）
     * @param postId 文章ID
     * @param publishedAt 发布时间
     * @param aggregateVersion 聚合根版本号
     */
    @JsonCreator
    public PostPublishedIntegrationEvent(@JsonProperty("eventId") String eventId,
                                         @JsonProperty("occurredAt") Instant occurredAt,
                                         @JsonProperty("postId") Long postId,
                                         @JsonProperty("authorId") Long authorId,
                                         @JsonProperty("title") String title,
                                         @JsonProperty("excerpt") String excerpt,
                                         @JsonProperty("publishedAt") Instant publishedAt,
                                         @JsonProperty("aggregateVersion") Long aggregateVersion,
                                         @JsonProperty("schemaVersion") Integer schemaVersion) {
        super(eventId, occurredAt, aggregateVersion, normalizeSchemaVersion(schemaVersion, authorId, title, excerpt));
        this.postId = postId;
        this.authorId = authorId;
        this.title = title;
        this.excerpt = excerpt;
        this.publishedAt = publishedAt;
    }

    public PostPublishedIntegrationEvent(String eventId,
                                         Instant occurredAt,
                                         Long postId,
                                         Long authorId,
                                         String title,
                                         String excerpt,
                                         Instant publishedAt,
                                         Long aggregateVersion) {
        this(eventId, occurredAt, postId, authorId, title, excerpt, publishedAt, aggregateVersion, 2);
    }

    @Deprecated
    public PostPublishedIntegrationEvent(String eventId,
                                         Instant occurredAt,
                                         Long postId,
                                         Instant publishedAt,
                                         Long aggregateVersion) {
        this(eventId, occurredAt, postId, null, null, null, publishedAt, aggregateVersion, 1);
    }

    @Override
    public String getTag() {
        return "published";
    }
    
    @Override
    public Long getAggregateId() {
        return postId;
    }

    private static int normalizeSchemaVersion(Integer schemaVersion,
                                              Long authorId,
                                              String title,
                                              String excerpt) {
        if (schemaVersion != null) {
            return schemaVersion;
        }
        return authorId != null || title != null || excerpt != null ? 2 : 1;
    }
}
