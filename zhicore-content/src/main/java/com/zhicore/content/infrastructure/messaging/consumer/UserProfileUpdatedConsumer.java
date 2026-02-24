package com.zhicore.content.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.integration.messaging.user.UserProfileUpdatedIntegrationEvent;
import com.zhicore.content.application.port.cache.CacheRepository;
import com.zhicore.content.application.port.repo.ConsumedEventRepository;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.OwnerSnapshot;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户资料更新事件消费者
 * 
 * 使用数据库表实现幂等性，避免重启后丢失去重信息。
 * 通过版本号机制防止消息乱序导致的数据不一致。
 * 
 * 核心功能：
 * - 消费用户资料更新事件
 * - 使用数据库主键约束实现幂等性检查
 * - 使用版本号机制防止乱序更新
 * - 批量更新该作者的所有文章 OwnerSnapshot
 * - 失效相关缓存
 * 
 * 幂等性保证：
 * - 首次消费：插入成功，处理事件
 * - 重复消费：主键冲突，跳过处理
 * - 重启后：数据库记录仍在，保持幂等性
 * 
 * 版本控制：
 * - 只接受 profileVersion 更高的更新
 * - 防止旧事件覆盖新数据
 * - 自动处理乱序消息
 * 
 * 缓存失效策略：
 * - 文章详情缓存：post:detail:{postId}
 * - 作者列表缓存：post:list:author:{authorId}:*
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "user-profile-events",
    consumerGroup = "zhicore-content-profile-consumer",
    selectorExpression = "profile-updated"
)
public class UserProfileUpdatedConsumer implements RocketMQListener<String> {
    
    private final PostRepository postRepository;
    private final CacheRepository cacheRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 处理用户资料更新消息
     * 
     * <p>处理流程：
     * <ol>
     *   <li>解析 JSON 消息为 UserProfileUpdatedIntegrationEvent</li>
     *   <li>幂等性检查：尝试插入消费记录</li>
     *   <li>如果是重复消息，直接返回</li>
     *   <li>构造新的 OwnerSnapshot</li>
     *   <li>查询该作者的所有文章</li>
     *   <li>遍历文章，使用版本号检查更新</li>
     *   <li>失效相关缓存</li>
     *   <li>记录处理结果</li>
     * </ol>
     * 
     * <p>事务处理：
     * <ul>
     *   <li>使用 @Transactional 确保幂等性检查和数据更新的原子性</li>
     *   <li>如果处理失败，事务回滚，消息会重新投递</li>
     *   <li>重新投递时，幂等性检查会阻止重复处理</li>
     * </ul>
     * 
     * @param message JSON 格式的消息内容
     * @throws RuntimeException 当消息解析或处理失败时抛出，触发重试
     */
    @Override
    @Transactional
    public void onMessage(String message) {
        try {
            log.info("收到用户资料更新消息: {}", message);
            
            // 1. 解析消息（使用新的集成事件）
            UserProfileUpdatedIntegrationEvent event = objectMapper.readValue(
                message, UserProfileUpdatedIntegrationEvent.class);
            
            log.info("解析用户资料更新事件: userId={}, nickname={}, avatar={}, version={}", 
                    event.getUserId(), event.getNickname(), event.getAvatar(), 
                    event.getAggregateVersion());
            
            // 2. 转换为值对象
            UserId userId = UserId.of(event.getUserId());
            
            // 3. 幂等性检查：使用事件ID
            boolean isNewEvent = consumedEventRepository.tryInsert(
                event.getEventId(),
                "UserProfileUpdated",
                "zhicore-content-profile-consumer"
            );
            
            if (!isNewEvent) {
                log.info("事件已处理过，跳过: eventId={}, userId={}, version={}", 
                        event.getEventId(), event.getUserId(), event.getAggregateVersion());
                return;
            }
            
            // 4. 构造新的 OwnerSnapshot（使用值对象）
            OwnerSnapshot newSnapshot = new OwnerSnapshot(
                userId,  // 使用 UserId 值对象
                event.getNickname(),
                event.getAvatar(),
                event.getAggregateVersion()
            );
            
            // 5. 查询该作者的所有文章（分批处理，避免一次加载过多）
            int pageSize = 100;
            int pageNumber = 0;
            int totalUpdated = 0;
            int totalProcessed = 0;
            
            while (true) {
                Pageable pageable = PageRequest.of(pageNumber, pageSize);
                List<Post> posts = postRepository.findByAuthor(userId, pageable);  // 使用 UserId 值对象
                
                if (posts.isEmpty()) {
                    break;  // 没有更多文章了
                }
                
                totalProcessed += posts.size();
                
                // 6. 遍历文章，使用版本号检查更新
                for (Post post : posts) {
                    // 版本检查：只接受更高的版本
                    if (newSnapshot.isNewerThan(post.getOwnerSnapshot())) {
                        boolean updated = post.updateOwnerSnapshot(newSnapshot);
                        if (updated) {
                            postRepository.update(post);
                            totalUpdated++;
                            
                            // 7. 失效文章详情缓存
                            cacheRepository.delete(PostRedisKeys.detail(post.getId()));
                        }
                    }
                }
                
                // 如果返回的文章数少于 pageSize，说明已经是最后一页
                if (posts.size() < pageSize) {
                    break;
                }
                
                pageNumber++;
            }
            
            // 8. 失效作者列表缓存（使用模式匹配）
            try {
                cacheRepository.deletePattern(PostRedisKeys.listAuthor(userId) + ":*");
            } catch (UnsupportedOperationException e) {
                // 生产环境可能禁用 deletePattern，记录警告但不影响主流程
                log.warn("无法使用 deletePattern 失效缓存，生产环境建议使用版本号 key: {}", e.getMessage());
            }
            
            log.info("用户资料更新处理完成: userId={}, version={}, 处理文章数={}, 实际更新数={}", 
                    event.getUserId(), event.getAggregateVersion(), totalProcessed, totalUpdated);
            
        } catch (Exception e) {
            log.error("处理用户资料更新消息失败: {}, 错误: {}", message, e.getMessage(), e);
            // 抛出异常触发 RocketMQ 重试机制
            throw new RuntimeException("处理用户资料更新消息失败: " + e.getMessage(), e);
        }
    }
}
