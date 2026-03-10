package com.zhicore.content.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.integration.messaging.post.PostStatsUpdatedIntegrationEvent;
import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.content.application.port.repo.ConsumedEventRepository;
import com.zhicore.content.application.port.repo.PostStatsRepository;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 文章统计信息更新事件消费者
 * 
 * 使用数据库表实现幂等性，避免重启后丢失去重信息。
 * 采用覆盖式更新策略，简化并发控制。
 * 
 * 核心功能：
 * - 消费文章统计信息更新事件
 * - 使用数据库主键约束实现幂等性检查
 * - 使用 upsert 操作覆盖式更新统计数据
 * - 失效相关缓存
 * 
 * 幂等性保证：
 * - 首次消费：插入成功，处理事件
 * - 重复消费：主键冲突，跳过处理
 * - 重启后：数据库记录仍在，保持幂等性
 * 
 * 覆盖式更新：
 * - 事件包含完整的统计数据（不是增量）
 * - 直接使用新值覆盖旧值
 * - 避免了增量更新的并发问题
 * - 简化了消费者逻辑
 * 
 * 缓存失效策略：
 * - 文章详情缓存：post:detail:{postId}
 * - 统计信息缓存：post:stats:{postId}
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "post-stats-events",
    consumerGroup = "zhicore-content-stats-consumer",
    selectorExpression = "stats-updated"
)
public class StatsUpdatedConsumer implements RocketMQListener<String> {
    
    private final PostStatsRepository postStatsRepository;
    private final CacheStore cacheStore;
    private final ConsumedEventRepository consumedEventRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 处理文章统计信息更新消息
     * 
     * 处理流程：
     * 1. 解析 JSON 消息为 PostStatsUpdatedIntegrationEvent
     * 2. 幂等性检查：尝试插入消费记录
     * 3. 如果是重复消息，直接返回
     * 4. 构造新的 PostStats 对象
     * 5. 使用 upsert 操作更新统计信息
     * 6. 失效相关缓存
     * 7. 记录处理结果
     * 
     * 事务处理：
     * - 使用 @Transactional 确保幂等性检查和数据更新的原子性
     * - 如果处理失败，事务回滚，消息会重新投递
     * - 重新投递时，幂等性检查会阻止重复处理
     * 
     * @param message JSON 格式的消息内容
     * @throws RuntimeException 当消息解析或处理失败时抛出，触发重试
     */
    @Override
    @Transactional
    public void onMessage(String message) {
        try {
            log.info("收到文章统计信息更新消息: {}", message);
            
            // 1. 解析消息（使用新的集成事件）
            PostStatsUpdatedIntegrationEvent event = objectMapper.readValue(
                message, PostStatsUpdatedIntegrationEvent.class);
            
            log.info("解析文章统计信息更新事件: eventId={}, postId={}, viewCount={}, likeCount={}, favoriteCount={}, commentCount={}", 
                    event.getEventId(), event.getPostId(), event.getViewCount(), 
                    event.getLikeCount(), event.getFavoriteCount(), event.getCommentCount());
            
            // 2. 转换为值对象
            PostId postId = PostId.of(event.getPostId());
            
            // 3. 幂等性检查：使用事件ID
            boolean isNewEvent = consumedEventRepository.tryInsert(
                event.getEventId(),
                "PostStatsUpdated",
                "zhicore-content-stats-consumer"
            );
            
            if (!isNewEvent) {
                log.info("事件已处理过，跳过: eventId={}, postId={}", 
                        event.getEventId(), event.getPostId());
                return;
            }
            
            // 4. 构造新的 PostStats 对象（使用值对象）
            // 注意：集成事件使用 Instant，需要转换为 LocalDateTime
            LocalDateTime timestamp = LocalDateTime.ofInstant(
                event.getOccurredAt(), ZoneId.systemDefault());
            
            PostStats newStats = new PostStats(
                postId,  // 使用 PostId 值对象
                event.getViewCount() != null ? event.getViewCount().intValue() : 0,
                event.getLikeCount() != null ? event.getLikeCount().intValue() : 0,
                event.getCommentCount() != null ? event.getCommentCount().intValue() : 0,
                event.getFavoriteCount() != null ? event.getFavoriteCount().intValue() : 0,  // 使用 favoriteCount 而不是 shareCount
                timestamp
            );
            
            // 5. 使用 upsert 操作更新统计信息（覆盖式更新）
            postStatsRepository.upsert(postId, newStats);  // 使用 PostId 值对象
            
            log.info("统计信息更新成功: postId={}, viewCount={}, likeCount={}, favoriteCount={}, commentCount={}", 
                    event.getPostId(), event.getViewCount(), event.getLikeCount(), 
                    event.getFavoriteCount(), event.getCommentCount());
            
            // 6. 失效文章详情缓存
            cacheStore.delete(PostRedisKeys.detail(postId));
            
            // 7. 失效统计信息缓存（如果有单独的统计缓存）
            cacheStore.delete(
                    PostRedisKeys.viewCount(postId),
                    PostRedisKeys.likeCount(postId),
                    PostRedisKeys.commentCount(postId)
            );
            
            log.info("文章统计信息更新处理完成: eventId={}, postId={}", 
                    event.getEventId(), event.getPostId());
            
        } catch (Exception e) {
            log.error("处理文章统计信息更新消息失败: {}, 错误: {}", message, e.getMessage(), e);
            // 抛出异常触发 RocketMQ 重试机制
            throw new RuntimeException("处理文章统计信息更新消息失败: " + e.getMessage(), e);
        }
    }
}
