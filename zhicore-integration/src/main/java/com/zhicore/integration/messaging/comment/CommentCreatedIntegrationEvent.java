package com.zhicore.integration.messaging.comment;

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
    private final Long userId;
    
    /**
     * 评论内容（可能被截断以减少载荷）
     */
    private final String content;
    
    /**
     * 父评论ID（顶级评论为 null）
     */
    private final Long parentId;

    /**
     * 构造函数
     * 
     * @param eventId 事件ID（从领域事件复制）
     * @param occurredAt 事件发生时间（从领域事件复制）
     * @param commentId 评论ID
     * @param postId 文章ID
     * @param userId 评论者ID
     * @param content 评论内容
     * @param parentId 父评论ID
     * @param aggregateVersion 聚合根版本号（用于并发控制）
     */
    public CommentCreatedIntegrationEvent(String eventId, Instant occurredAt,
                                         Long commentId, Long postId, Long userId,
                                         String content, Long parentId,
                                         Long aggregateVersion) {
        super(eventId, occurredAt, aggregateVersion, 1);  // schemaVersion = 1
        this.commentId = commentId;
        this.postId = postId;
        this.userId = userId;
        this.content = content;
        this.parentId = parentId;
    }

    @Override
    public String getTag() {
        return "COMMENT_CREATED";
    }
    
    @Override
    public Long getAggregateId() {
        return commentId;
    }
}
