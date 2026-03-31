package com.zhicore.notification.application.service.command;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.notification.application.port.store.NotificationAggregationStore;
import com.zhicore.notification.application.port.store.NotificationUnreadCountStore;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationGroupState;
import com.zhicore.notification.domain.repository.NotificationGroupStateRepository;
import com.zhicore.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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

    private static final Duration UNREAD_COUNT_TTL = Duration.ofMinutes(5);

    private final NotificationRepository notificationRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final NotificationUnreadCountStore notificationUnreadCountStore;
    private final NotificationAggregationStore notificationAggregationStore;
    private final NotificationGroupStateRepository notificationGroupStateRepository;

    @Transactional
    public Notification createLikeNotification(Long recipientId, Long actorId, String targetType, Long targetId) {
        Long id = generateId();
        Notification notification = Notification.createLikeNotification(
                id, recipientId, actorId, targetType, targetId);

        persistNotification(notification);

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

        persistNotification(notification);

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

        persistNotification(notification);

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

        persistNotification(notification);

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
    public Notification createPostPublishedNotification(Long recipientId,
                                                        Long authorId,
                                                        Long postId,
                                                        String groupKey,
                                                        String content) {
        Long id = generateId();
        Notification notification = Notification.createPostPublishedNotification(
                id, recipientId, authorId, postId, groupKey, content);

        persistNotification(notification);

        log.info("创建关注作者发文通知: id={}, recipient={}, authorId={}, postId={}",
                id, recipientId, authorId, postId);

        return notification;
    }

    @Transactional
    public Notification createPostPublishedDigestNotification(Long recipientId,
                                                              String groupKey,
                                                              String content) {
        Long id = generateId();
        Notification notification = Notification.createPostPublishedDigestNotification(
                id,
                recipientId,
                groupKey,
                content
        );

        persistNotification(notification);

        log.info("创建关注作者发文摘要通知: id={}, recipient={}", id, recipientId);

        return notification;
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

        int affectedRows = notificationRepository.markAsRead(notificationId, userId);
        if (affectedRows <= 0) {
            log.debug("通知已被其他请求更新，跳过未读递减: notificationId={}, userId={}", notificationId, userId);
            return;
        }
        int projectionAffectedRows = notificationGroupStateRepository.decrementUnreadCount(
                userId, NotificationGroupState.resolveGroupKey(notification));
        if (projectionAffectedRows <= 0) {
            log.warn("聚合投影未命中单条已读更新，后续读取将回退数据库聚合: notificationId={}, userId={}", notificationId, userId);
        }
        decrementUnreadCount(userId);
        evictAggregationCache(userId);

        log.debug("标记通知已读: notificationId={}, userId={}", notificationId, userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        int affectedRows = notificationRepository.markAllAsRead(userId);
        if (affectedRows <= 0) {
            log.debug("没有未读通知需要批量更新: userId={}", userId);
            return;
        }
        int projectionAffectedRows = notificationGroupStateRepository.markAllAsRead(userId);
        if (projectionAffectedRows <= 0) {
            log.warn("聚合投影未命中批量已读更新，后续读取将回退数据库聚合: userId={}", userId);
        }
        resetUnreadCount(userId);
        evictAggregationCache(userId);

        log.info("批量标记所有通知已读: userId={}, updatedRows={}", userId, updatedRows);
    }

    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (!response.isSuccess() || response.getData() == null) {
            log.error("生成通知ID失败: {}", response.getMessage());
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, "通知ID生成失败");
        }
        return response.getData();
    }

    private void persistNotification(Notification notification) {
        notificationRepository.save(notification);
        notificationGroupStateRepository.upsertOnNotificationCreated(notification);
        incrementUnreadCount(notification.getRecipientId());
        evictAggregationCache(notification.getRecipientId());
    }

    private void incrementUnreadCount(Long userId) {
        try {
            notificationUnreadCountStore.increment(userId, 1, UNREAD_COUNT_TTL);
        } catch (Exception e) {
            log.warn("递增通知未读缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    private Optional<Notification> saveIfAbsent(Notification notification) {
        if (!notificationRepository.saveIfAbsent(notification)) {
            log.info("通知已存在，跳过重复写入: notificationId={}, recipient={}, type={}",
                    notification.getId(), notification.getRecipientId(), notification.getType());
            return Optional.empty();
        }

        notificationGroupStateRepository.upsertOnNotificationCreated(notification);
        incrementUnreadCount(notification.getRecipientId());
        evictAggregationCache(notification.getRecipientId());
        return Optional.of(notification);
    }

    private void decrementUnreadCount(Long userId) {
        try {
            notificationUnreadCountStore.decrement(userId, 1);
        } catch (Exception e) {
            log.warn("递减通知未读缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    private void resetUnreadCount(Long userId) {
        try {
            notificationUnreadCountStore.set(userId, 0, UNREAD_COUNT_TTL);
        } catch (Exception e) {
            log.warn("重置通知未读缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    private void evictAggregationCache(Long userId) {
        try {
            notificationAggregationStore.evictUser(userId);
        } catch (Exception e) {
            log.warn("清除通知聚合缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }
}
