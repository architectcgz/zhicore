package com.zhicore.api.event.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户资料变更事件
 * 
 * <p>当用户修改昵称或头像时发送此事件，用于通知其他服务同步更新冗余的用户信息。
 * Post Service 消费此事件以更新文章的作者信息冗余字段，确保数据的最终一致性。</p>
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li>用户修改昵称时，User Service 发送此事件</li>
 *   <li>用户修改头像时，User Service 发送此事件</li>
 *   <li>Post Service 消费此事件，批量更新该用户所有文章的作者信息</li>
 * </ul>
 * 
 * <h3>版本号机制</h3>
 * <p>version 字段用于实现幂等性和防止消息乱序：</p>
 * <ul>
 *   <li>每次用户资料更新时，version 在数据库层原子递增</li>
 *   <li>消费者只更新 owner_profile_version < version 的记录</li>
 *   <li>防止旧事件覆盖新数据</li>
 *   <li>支持消息重复投递（幂等性）</li>
 * </ul>
 * 
 * <h3>消息顺序性</h3>
 * <p>使用 userId 作为 RocketMQ 的 sharding key，尽量保证同一用户的消息按顺序消费。
 * 但即使消息乱序到达，版本号机制也能防止数据错误。</p>
 * 
 * @author ZhiCore-microservice
 * @since 1.0.0
 * @see com.zhicore.post.infrastructure.mq.UserProfileChangedConsumer
 * @see com.zhicore.post.application.service.PostAuthorSyncService
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdatedEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户ID
     * 
     * <p>标识哪个用户的资料发生了变更。
     * 同时作为 RocketMQ 消息的 sharding key，确保同一用户的消息有序。</p>
     */
    private Long userId;
    
    /**
     * 新昵称
     * 
     * <p>用户更新后的昵称，最大长度 50 个字符。
     * 即使只修改头像，也会包含完整的昵称信息。</p>
     */
    private String nickname;
    
    /**
     * 新头像文件ID
     * 
     * <p>用户更新后的头像文件 ID（UUIDv7 格式），可为 null 表示用户删除了头像。
     * 即使只修改昵称，也会包含完整的头像信息。</p>
     * 
     * <p>注意：这是文件服务返回的文件 ID，不是完整的 URL。
     * 消费者需要调用文件服务将 ID 转换为可访问的 URL。</p>
     */
    private String avatarId;
    
    /**
     * 资料版本号（递增）
     * 
     * <p>用于防止消息乱序和重复更新。每次用户资料更新时，版本号在数据库层原子递增。</p>
     * 
     * <h4>版本号机制</h4>
     * <ul>
     *   <li>初始值为 0</li>
     *   <li>每次更新资料时，通过 SQL 的 <code>profile_version = profile_version + 1</code> 原子递增</li>
     *   <li>消费者使用 <code>WHERE owner_profile_version < :version</code> 条件更新</li>
     *   <li>如果 version <= 当前版本，更新会被跳过（幂等性）</li>
     * </ul>
     * 
     * <h4>示例</h4>
     * <pre>
     * // 用户首次更新资料
     * version = 1
     * 
     * // 用户第二次更新资料
     * version = 2
     * 
     * // 如果消息乱序，version=1 的事件后到达
     * // WHERE owner_profile_version < 1 不满足（当前已是 2）
     * // 更新被跳过，防止旧数据覆盖新数据
     * </pre>
     */
    private Long version;
    
    /**
     * 更新时间戳
     * 
     * <p>用户资料的更新时间，用于日志记录和问题排查。</p>
     */
    private LocalDateTime updatedAt;
}
