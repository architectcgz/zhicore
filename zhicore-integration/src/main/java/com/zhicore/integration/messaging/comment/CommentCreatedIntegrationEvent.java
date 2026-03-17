package com.zhicore.integration.messaging.comment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

/**
 * 评论创建集成事件
 * 
 * 用于跨服务通信，当用户创建评论时发布此事件。
 * 只包含跨服务必需的最小信息。
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li>用户创建评论时，Comment Service 发送此事件</li>
 *   <li>Ranking Service 消费此事件，更新文章热度排名</li>
 *   <li>Notification Service 消费此事件，创建评论通知</li>
 * </ul>
 * 
 * @author ZhiCore Team
 */
@Getter
public class CommentCreatedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 评论ID
     */
    private final Long commentId;
    
    /**
     * 文章ID
     */
    private final Long postId;
    
    /**
     * 评论者ID
     */
    private final Long postOwnerId;
    
    /**
     * 评论作者ID
     */
    private final Long commentAuthorId;
    
    /**
     * 父评论ID（顶级评论为 null）
     */
    private final Long parentId;

    /**
     * 被回复用户ID。
     */
    private final Long replyToUserId;

    /**
     * 评论内容（可能被截断以减少载荷）。
     */
    private final String commentContent;

    /**
     * 构造函数
     * 
     * @param eventId 事件ID（从领域事件复制）
     * @param occurredAt 事件发生时间（从领域事件复制）
     * @param commentId 评论ID
     * @param postId 文章ID
     * @param postOwnerId 文章作者 ID
     * @param commentAuthorId 评论作者 ID
     * @param parentId 父评论ID
     * @param replyToUserId 被回复用户 ID
     * @param commentContent 评论内容
     * @param aggregateVersion 聚合根版本号（用于并发控制）
     */
    @JsonCreator
    public CommentCreatedIntegrationEvent(@JsonProperty("eventId") String eventId,
                                          @JsonProperty("occurredAt") Instant occurredAt,
                                          @JsonProperty("commentId") Long commentId,
                                          @JsonProperty("postId") Long postId,
                                          @JsonProperty("postOwnerId") Long postOwnerId,
                                          @JsonProperty("commentAuthorId") Long commentAuthorId,
                                          @JsonProperty("parentId") Long parentId,
                                          @JsonProperty("replyToUserId") Long replyToUserId,
                                          @JsonProperty("commentContent") String commentContent,
                                          @JsonProperty("aggregateVersion") Long aggregateVersion) {
        super(eventId, occurredAt, aggregateVersion, 1);  // schemaVersion = 1
        this.commentId = commentId;
        this.postId = postId;
        this.postOwnerId = postOwnerId;
        this.commentAuthorId = commentAuthorId;
        this.parentId = parentId;
        this.replyToUserId = replyToUserId;
        this.commentContent = commentContent;
    }

    @Override
    public String getTag() {
        return TopicConstants.TAG_COMMENT_CREATED;
    }
    
    @Override
    public Long getAggregateId() {
        return commentId;
    }
}
