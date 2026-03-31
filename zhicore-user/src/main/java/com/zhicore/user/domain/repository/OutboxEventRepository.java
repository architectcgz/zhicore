package com.zhicore.user.domain.repository;

import com.zhicore.user.domain.model.OutboxEvent;
import com.zhicore.user.domain.model.OutboxEventStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Outbox 事件仓储接口
 * 
 * <p>用于 Transactional Outbox 模式的事件持久化和查询</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li>在业务事务内保存新的 outbox 事件</li>
 *   <li>基于 claim 语义抢占一批可投递事件</li>
 *   <li>更新事件的处理状态、重试信息与 claim 信息</li>
 *   <li>查询失败或死信事件，供管理操作使用</li>
 * </ul>
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

    OffsetDateTime findOldestPendingCreatedAt();

    long countSucceededSince(OffsetDateTime since);

    long countFailedSince(OffsetDateTime since, int defaultMaxRetries);

    long countDeadSince(OffsetDateTime since, int defaultMaxRetries);

    /**
     * claim 一批当前可投递事件。
     *
     * <p>约束：
     * - 同一 shardingKey 仅允许最早未完成事件进入处理
     * - PROCESSING 超时事件允许被回收
     */
    List<OutboxEvent> claimRetryableEvents(OffsetDateTime now,
                                           OffsetDateTime reclaimBefore,
                                           String claimedBy,
                                           int limit);
}
