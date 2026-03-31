package com.zhicore.notification.infrastructure.repository;

import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationGroupState;
import com.zhicore.notification.domain.repository.NotificationGroupStateRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationGroupStateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 通知聚合组状态仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class NotificationGroupStateRepositoryImpl implements NotificationGroupStateRepository {

    private final NotificationGroupStateMapper notificationGroupStateMapper;

    @Override
    public void upsertOnNotificationCreated(Notification notification) {
        notificationGroupStateMapper.upsert(NotificationGroupState.fromNotification(notification));
    }

    @Override
    public List<NotificationGroupState> findPage(Long recipientId, int page, int size, int recentActorLimit) {
        return notificationGroupStateMapper.findPage(recipientId, page * size, size, recentActorLimit);
    }

    @Override
    public int countByRecipientId(Long recipientId) {
        return notificationGroupStateMapper.countByRecipientId(recipientId);
    }

    @Override
    public int sumUnreadCount(Long recipientId) {
        return notificationGroupStateMapper.sumUnreadCount(recipientId);
    }

    @Override
    public int decrementUnreadCount(Long recipientId, String groupKey) {
        return notificationGroupStateMapper.decrementUnreadCount(recipientId, groupKey);
    }

    @Override
    public int markAllAsRead(Long recipientId) {
        return notificationGroupStateMapper.markAllAsRead(recipientId);
    }
}
