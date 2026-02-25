package com.zhicore.user.domain.repository;

import com.zhicore.user.domain.model.OutboxEvent;
import com.zhicore.user.domain.model.OutboxEventStatus;

import java.util.List;
import java.util.Optional;

/**
 * Outbox 事件仓储接口
 * 
 * <p>用于 Transactional Outbox 模式的事件持久化和查询</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li>保存新的 Outbox 事件（在业务事务中）</li>
 *   <li>更新事件状态（发送成功/失败）</li>
 *   <li>查询待发送的事件（供后台任务扫描）</li>
 *   <li>查询失败的事件（供手动重试）</li>
 * </ul>
 * 
 * <h3>使用场景</h3>
 * <pre>
 * // 1. 在业务事务中保存事件
 * {@code @Transactional}
 * public void updateProfile(Long userId, UpdateProfileRequest req) {
 *     // 更新用户资料
 *     userRepository.update(user);
 *     
 *     // 保存 Outbox 事件（同一事务）
 *     OutboxEvent event = new OutboxEvent(...);
 *     outboxEventRepository.save(event);
 * }
 * 
 * // 2. 后台任务扫描并发送
 * {@code @Scheduled}(fixedDelay = 5000)
 * public void publishPendingEvents() {
 *     List&lt;OutboxEvent&gt; events = outboxEventRepository
 *         .findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, 100);
 *     
 *     for (OutboxEvent event : events) {
 *         // 发送到 RocketMQ
 *         // 更新状态
 *         outboxEventRepository.update(event);
 *     }
 * }
 * </pre>
 * 
 * @author System
 * @since 2026-02-19
 * @see OutboxEvent
 * @see OutboxEventStatus
 */
public interface OutboxEventRepository {
    
    /**
     * 保存新的 Outbox 事件
     * 
     * <p>在业务事务中调用，确保事件和业务数据的原子性</p>
     * 
     * @param event Outbox 事件对象
     * @throws IllegalArgumentException 如果 event 为 null
     */
    void save(OutboxEvent event);
    
    /**
     * 更新 Outbox 事件
     * 
     * <p>用于更新事件状态、重试次数、错误信息等</p>
     * 
     * @param event Outbox 事件对象
     * @throws IllegalArgumentException 如果 event 为 null 或 id 为空
     */
    void update(OutboxEvent event);
    
    /**
     * 根据 ID 查询 Outbox 事件
     * 
     * @param id 事件 ID（UUID）
     * @return Optional 包装的事件对象，不存在时返回 empty
     */
    Optional<OutboxEvent> findById(String id);
    
    /**
     * 查询指定状态的事件（按创建时间升序）
     * 
     * <p>用于后台任务扫描待发送的事件</p>
     * 
     * <p><strong>使用示例：</strong></p>
     * <pre>
     * // 每次最多处理 100 条 PENDING 事件
     * List&lt;OutboxEvent&gt; events = repository
     *     .findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING, 100);
     * </pre>
     * 
     * @param status 事件状态
     * @param limit 最大返回数量（建议不超过 1000）
     * @return 事件列表，按创建时间升序排列
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status, int limit);
    
    /**
     * 查询指定状态的所有事件
     * 
     * <p>用于查询失败的事件，供手动重试或监控</p>
     * 
     * <p><strong>注意：</strong>此方法不限制返回数量，仅用于管理操作</p>
     * 
     * @param status 事件状态
     * @return 事件列表
     */
    List<OutboxEvent> findByStatus(OutboxEventStatus status);
    
    /**
     * 统计指定状态的事件数量
     * 
     * <p>用于监控和告警</p>
     * 
     * @param status 事件状态
     * @return 事件数量
     */
    long countByStatus(OutboxEventStatus status);
}
