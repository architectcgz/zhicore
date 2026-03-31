package com.zhicore.notification.domain.repository;

import com.zhicore.notification.application.dto.AggregatedNotificationDTO;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通知仓储接口。
 */
public interface NotificationRepository {

    void save(Notification notification);

    boolean saveIfAbsent(Notification notification);

    Optional<Notification> findById(Long id);

    List<Notification> findByRecipientId(Long recipientId, int page, int size);

    List<AggregatedNotificationDTO> findAggregatedNotifications(Long recipientId, int page, int size);

    int countAggregatedGroups(Long recipientId);

    Optional<AggregatedNotificationDTO> findAggregatedNotificationByGroup(Long recipientId,
                                                                          NotificationType type,
                                                                          String targetType,
                                                                          Long targetId);

    List<Notification> findByGroup(Long recipientId,
                                   NotificationType type,
                                   String targetType,
                                   Long targetId,
                                   int limit);

    int countUnread(Long recipientId);

    Map<Integer, Integer> countUnreadByCategory(Long recipientId);

    int markAsRead(Long id, Long recipientId);

    int markAllAsRead(Long recipientId);

    void delete(Long id);
}
