package com.blog.notification.application.service;

import com.blog.api.client.IdGeneratorFeignClient;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ApiResponse;
import com.blog.notification.domain.model.Notification;
import com.blog.notification.domain.repository.NotificationRepository;
import com.blog.notification.infrastructure.cache.NotificationRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 通知应用服务
 *
 * @author Blog Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationApplicationService {

    private final NotificationRepository notificationRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration UNREAD_COUNT_TTL = Duration.ofMinutes(5);

    /**
     * 创建点赞通知
     *
     * @param recipientId 接收者ID
     * @param actorId 触发者ID
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @return 通知
     */
    @Transactional
    public Notification createLikeNotification(Long recipientId, Long actorId,
                                                String targetType, Long targetId) {
        Long id = generateId();
        Notification notification = Notification.createLikeNotification(
                id, recipientId, actorId, targetType, targetId);
        
        notificationRepository.save(notification);
        invalidateCache(recipientId);
        
        log.info("创建点赞通知: id={}, recipient={}, actor={}, target={}:{}",
                id, recipientId, actorId, targetType, targetId);
        
        return notification;
    }

    /**
     * 创建评论通知
     *
     * @param recipientId 接收者ID
     * @param actorId 触发者ID
     * @param postId 文章ID
     * @param commentId 评论ID
     * @param commentContent 评论内容
     * @return 通知
     */
    @Transactional
    public Notification createCommentNotification(Long recipientId, Long actorId,
                                                   Long postId, Long commentId,
                                                   String commentContent) {
        Long id = generateId();
        Notification notification = Notification.createCommentNotification(
                id, recipientId, actorId, postId, commentId, commentContent);
        
        notificationRepository.save(notification);
        invalidateCache(recipientId);
        
        log.info("创建评论通知: id={}, recipient={}, actor={}, postId={}, commentId={}",
                id, recipientId, actorId, postId, commentId);
        
        return notification;
    }

    /**
     * 创建回复通知
     *
     * @param recipientId 接收者ID
     * @param actorId 触发者ID
     * @param commentId 评论ID
     * @param replyContent 回复内容
     * @return 通知
     */
    @Transactional
    public Notification createReplyNotification(Long recipientId, Long actorId,
                                                 Long commentId, String replyContent) {
        Long id = generateId();
        Notification notification = Notification.createReplyNotification(
                id, recipientId, actorId, commentId, replyContent);
        
        notificationRepository.save(notification);
        invalidateCache(recipientId);
        
        log.info("创建回复通知: id={}, recipient={}, actor={}, commentId={}",
                id, recipientId, actorId, commentId);
        
        return notification;
    }

    /**
     * 创建关注通知
     *
     * @param recipientId 接收者ID
     * @param actorId 触发者ID
     * @return 通知
     */
    @Transactional
    public Notification createFollowNotification(Long recipientId, Long actorId) {
        Long id = generateId();
        Notification notification = Notification.createFollowNotification(id, recipientId, actorId);
        
        notificationRepository.save(notification);
        invalidateCache(recipientId);
        
        log.info("创建关注通知: id={}, recipient={}, actor={}", id, recipientId, actorId);
        
        return notification;
    }

    /**
     * 标记单条通知为已读
     *
     * @param notificationId 通知ID
     * @param userId 用户ID
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.markAsRead(notificationId, String.valueOf(userId));
        invalidateCache(userId);
        
        log.debug("标记通知已读: notificationId={}, userId={}", notificationId, userId);
    }

    /**
     * 批量标记所有通知为已读
     *
     * @param userId 用户ID
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(String.valueOf(userId));
        invalidateCache(userId);
        
        log.info("批量标记所有通知已读: userId={}", userId);
    }

    /**
     * 获取未读通知数量
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    public int getUnreadCount(Long userId) {
        String key = NotificationRedisKeys.unreadCount(String.valueOf(userId));
        
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return (Integer) cached;
            }
        } catch (Exception e) {
            log.warn("获取未读计数缓存失败: {}", e.getMessage());
        }
        
        int count = notificationRepository.countUnread(String.valueOf(userId));
        
        try {
            redisTemplate.opsForValue().set(key, count, UNREAD_COUNT_TTL);
        } catch (Exception e) {
            log.warn("缓存未读计数失败: {}", e.getMessage());
        }
        
        return count;
    }

    /**
     * 生成通知ID
     */
    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (!response.isSuccess() || response.getData() == null) {
            log.error("生成通知ID失败: {}", response.getMessage());
            throw new BusinessException("通知ID生成失败");
        }
        return response.getData();
    }

    /**
     * 清除用户的通知缓存
     */
    private void invalidateCache(Long userId) {
        try {
            // 清除未读计数缓存
            redisTemplate.delete(NotificationRedisKeys.unreadCount(String.valueOf(userId)));
            
            // 清除聚合列表缓存
            var keys = redisTemplate.keys(NotificationRedisKeys.aggregatedListPattern(String.valueOf(userId)));
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("清除通知缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }
}
