package com.zhicore.notification.application.service.command;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.application.port.store.NotificationAggregationStore;
import com.zhicore.notification.application.port.store.NotificationUnreadCountStore;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 通知写服务。
 *
 * 负责通知创建、已读变更与缓存失效，不承载查询职责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final NotificationUnreadCountStore notificationUnreadCountStore;
    private final NotificationAggregationStore notificationAggregationStore;

    @Transactional
    public Notification createLikeNotification(Long recipientId, Long actorId, String targetType, Long targetId) {
        Long id = generateId();
        Notification notification = Notification.createLikeNotification(
                id, recipientId, actorId, targetType, targetId);

        notificationRepository.save(notification);
        invalidateCache(recipientId);

        log.info("创建点赞通知: id={}, recipient={}, actor={}, target={}:{}",
                id, recipientId, actorId, targetType, targetId);

        return notification;
    }

    @Transactional
    public Optional<Notification> createLikeNotificationIfAbsent(Long notificationId,
                                                                 Long recipientId,
                                                                 Long actorId,
                                                                 String targetType,
                                                                 Long targetId) {
        Notification notification = Notification.createLikeNotification(
                notificationId, recipientId, actorId, targetType, targetId);
        return saveIfAbsent(notification);
    }

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

    @Transactional
    public Optional<Notification> createCommentNotificationIfAbsent(Long notificationId,
                                                                    Long recipientId,
                                                                    Long actorId,
                                                                    Long postId,
                                                                    Long commentId,
                                                                    String commentContent) {
        Notification notification = Notification.createCommentNotification(
                notificationId, recipientId, actorId, postId, commentId, commentContent);
        return saveIfAbsent(notification);
    }

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

    @Transactional
    public Optional<Notification> createReplyNotificationIfAbsent(Long notificationId,
                                                                  Long recipientId,
                                                                  Long actorId,
                                                                  Long commentId,
                                                                  String replyContent) {
        Notification notification = Notification.createReplyNotification(
                notificationId, recipientId, actorId, commentId, replyContent);
        return saveIfAbsent(notification);
    }

    @Transactional
    public Notification createFollowNotification(Long recipientId, Long actorId) {
        Long id = generateId();
        Notification notification = Notification.createFollowNotification(id, recipientId, actorId);

        notificationRepository.save(notification);
        invalidateCache(recipientId);

        log.info("创建关注通知: id={}, recipient={}, actor={}", id, recipientId, actorId);

        return notification;
    }

    @Transactional
    public Optional<Notification> createFollowNotificationIfAbsent(Long notificationId,
                                                                   Long recipientId,
                                                                   Long actorId) {
        Notification notification = Notification.createFollowNotification(notificationId, recipientId, actorId);
        return saveIfAbsent(notification);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getRecipientId().equals(userId)) {
            throw new BusinessException(ResultCode.RESOURCE_ACCESS_DENIED, "无权访问该通知");
        }
        if (notification.isRead()) {
            log.debug("通知已读，跳过重复更新: notificationId={}, userId={}", notificationId, userId);
            return;
        }

        notificationRepository.markAsRead(notificationId, userId);
        invalidateCache(userId);

        log.debug("标记通知已读: notificationId={}, userId={}", notificationId, userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
        invalidateCache(userId);

        log.info("批量标记所有通知已读: userId={}", userId);
    }

    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (!response.isSuccess() || response.getData() == null) {
            log.error("生成通知ID失败: {}", response.getMessage());
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "通知ID生成失败");
        }
        return response.getData();
    }

    private void invalidateCache(Long userId) {
        try {
            notificationUnreadCountStore.evict(userId);
            notificationAggregationStore.evictUser(userId);
        } catch (Exception e) {
            log.warn("清除通知缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    private Optional<Notification> saveIfAbsent(Notification notification) {
        if (!notificationRepository.saveIfAbsent(notification)) {
            log.info("通知已存在，跳过重复写入: notificationId={}, recipient={}, type={}",
                    notification.getId(), notification.getRecipientId(), notification.getType());
            return Optional.empty();
        }

        invalidateCache(notification.getRecipientId());
        return Optional.of(notification);
    }
}
