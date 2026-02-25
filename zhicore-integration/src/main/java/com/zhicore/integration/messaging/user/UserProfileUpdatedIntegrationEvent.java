package com.zhicore.integration.messaging.user;

import com.zhicore.integration.messaging.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;

/**
 * 用户资料更新集成事件
 * 
 * 用于跨服务通信，当用户修改昵称、头像或个人简介时发布此事件。
 * 只包含跨服务必需的最小信息。
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li>用户修改昵称时，User Service 发送此事件</li>
 *   <li>用户修改头像时，User Service 发送此事件</li>
 *   <li>用户修改个人简介时，User Service 发送此事件</li>
 *   <li>Content Service 消费此事件，批量更新该用户所有文章的作者信息</li>
 * </ul>
 * 
 * <h3>版本号机制</h3>
 * <p>aggregateVersion 字段用于实现幂等性和防止消息乱序：</p>
 * <ul>
 *   <li>每次用户资料更新时，version 在数据库层原子递增</li>
 *   <li>消费者只更新 owner_profile_version < aggregateVersion 的记录</li>
 *   <li>防止旧事件覆盖新数据</li>
 *   <li>支持消息重复投递（幂等性）</li>
 * </ul>
 * 
 * @author ZhiCore Team
 */
@Getter
public class UserProfileUpdatedIntegrationEvent extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private final Long userId;
    
    /**
     * 用户名（登录名）
     */
    private final String username;
    
    /**
     * 新昵称
     */
    private final String nickname;
    
    /**
     * 新头像文件ID（可为 null）
     */
    private final String avatar;
    
    /**
     * 个人简介（可为 null）
     */
    private final String bio;

    /**
     * 构造函数
     * 
     * @param eventId 事件ID（从领域事件复制）
     * @param occurredAt 事件发生时间（从领域事件复制）
     * @param userId 用户ID
     * @param username 用户名
     * @param nickname 昵称
     * @param avatar 头像文件ID
     * @param bio 个人简介
     * @param aggregateVersion 聚合根版本号（用于并发控制）
     */
    public UserProfileUpdatedIntegrationEvent(String eventId, Instant occurredAt,
                                             Long userId, String username, String nickname,
                                             String avatar, String bio,
                                             Long aggregateVersion) {
        super(eventId, occurredAt, aggregateVersion, 1);  // schemaVersion = 1
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.avatar = avatar;
        this.bio = bio;
    }

    @Override
    public String getTag() {
        return "USER_PROFILE_UPDATED";
    }
    
    @Override
    public Long getAggregateId() {
        return userId;
    }
}
