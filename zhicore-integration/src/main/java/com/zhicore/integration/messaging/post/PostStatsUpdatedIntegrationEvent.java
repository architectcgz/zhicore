package com.zhicore.integration.messaging.post;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

/**
 * 文章统计更新集成事件
 * 
 * 用于跨服务通信，当文章的统计信息（浏览量、点赞数、评论数等）发生变化时发布此事件。
 * 只包含跨服务必需的最小信息。
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li>用户点赞/取消点赞文章时，Like Service 发送此事件</li>
 *   <li>用户收藏/取消收藏文章时，Favorite Service 发送此事件</li>
 *   <li>用户评论/删除评论时，Comment Service 发送此事件</li>
 *   <li>Content Service 消费此事件，更新文章统计信息</li>
 * </ul>
 * 
 * <h3>覆盖式更新策略</h3>
 * <p>事件包含完整的统计数据（不是增量），消费者使用 upsert 操作直接覆盖旧值。
 * 这简化了并发控制，避免了增量更新的复杂性。</p>
 * 
 * @author ZhiCore Team
 */
@Getter
public class PostStatsUpdatedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private final Long postId;
    
    /**
     * 浏览量（覆盖式更新）
     */
    private final Long viewCount;
    
    /**
     * 点赞数（覆盖式更新）
     */
    private final Long likeCount;
    
    /**
     * 收藏数（覆盖式更新）
     */
    private final Long favoriteCount;
    
    /**
     * 评论数（覆盖式更新）
     */
    private final Long commentCount;

    /**
     * 构造函数
     * 
     * @param eventId 事件ID（从领域事件复制）
     * @param occurredAt 事件发生时间（从领域事件复制）
     * @param postId 文章ID
     * @param viewCount 浏览量
     * @param likeCount 点赞数
     * @param favoriteCount 收藏数
     * @param commentCount 评论数
     * @param aggregateVersion 聚合根版本号（用于并发控制）
     */
    @JsonCreator
    public PostStatsUpdatedIntegrationEvent(@JsonProperty("eventId") String eventId,
                                            @JsonProperty("occurredAt") Instant occurredAt,
                                            @JsonProperty("postId") Long postId,
                                            @JsonProperty("viewCount") Long viewCount,
                                            @JsonProperty("likeCount") Long likeCount,
                                            @JsonProperty("favoriteCount") Long favoriteCount,
                                            @JsonProperty("commentCount") Long commentCount,
                                            @JsonProperty("aggregateVersion") Long aggregateVersion) {
        super(eventId, occurredAt, aggregateVersion, 1);  // schemaVersion = 1
        this.postId = postId;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.favoriteCount = favoriteCount;
        this.commentCount = commentCount;
    }

    @Override
    public String getTag() {
        return "POST_STATS_UPDATED";
    }
    
    @Override
    public Long getAggregateId() {
        return postId;
    }
}
