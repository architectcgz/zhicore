package com.zhicore.notification.domain.repository;

import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationGroupState;

import java.util.List;

/**
 * 通知聚合组状态仓储。
 */
public interface NotificationGroupStateRepository {

    /**
     * 新通知写入后同步更新聚合组状态。
     */
    void upsertOnNotificationCreated(Notification notification);

    /**
     * 分页读取用户聚合组状态。
     */
    List<NotificationGroupState> findPage(Long recipientId, int page, int size, int recentActorLimit);

    /**
     * 统计用户聚合组数量。
     */
    int countByRecipientId(Long recipientId);

    /**
     * 统计用户聚合投影中的未读总数。
     */
    int sumUnreadCount(Long recipientId);

    /**
     * 单条通知已读后递减聚合组未读数。
     */
    int decrementUnreadCount(Long recipientId, String groupKey);

    /**
     * 全部已读后重置聚合组未读数。
     */
    int markAllAsRead(Long recipientId);
}
