package com.zhicore.integration.messaging.user;

import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

/**
 * 用户关注集成事件
 * 
 * 用于跨服务通信，当用户关注另一个用户时发布此事件。
 * 只包含跨服务必需的最小信息。
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li>用户关注另一个用户时，User Service 发送此事件</li>
 *   <li>Notification Service 消费此事件，创建关注通知</li>
 * </ul>
 * 
 * @author ZhiCore Team
 */
@Getter
public class UserFollowedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 关注者ID（发起关注的用户）
     */
    private final Long followerId;
    
    /**
     * 被关注者ID（被关注的用户）
     */
    private final Long followedId;

    /**
     * 构造函数
     * 
     * @param eventId 事件ID（从领域事件复制）
     * @param occurredAt 事件发生时间（从领域事件复制）
     * @param followerId 关注者ID
     * @param followedId 被关注者ID
     * @param aggregateVersion 聚合根版本号（用于并发控制）
     */
    public UserFollowedIntegrationEvent(String eventId, Instant occurredAt,
                                       Long followerId, Long followedId,
                                       Long aggregateVersion) {
        super(eventId, occurredAt, aggregateVersion, 1);  // schemaVersion = 1
        this.followerId = followerId;
        this.followedId = followedId;
    }

    @Override
    public String getTag() {
        return "USER_FOLLOWED";
    }
    
    @Override
    public Long getAggregateId() {
        return followerId;  // 使用关注者ID作为聚合根ID
    }
}
