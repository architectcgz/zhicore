package com.zhicore.notification.domain.repository;

import com.zhicore.notification.domain.model.NotificationDelivery;

import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryRepository {

    boolean saveIfAbsent(NotificationDelivery delivery);

    Optional<NotificationDelivery> findById(Long deliveryId);

    List<NotificationDelivery> query(Long campaignId,
                                     Long recipientId,
                                     String channel,
                                     String status,
                                     int page,
                                     int size);

    long count(Long campaignId, Long recipientId, String channel, String status);

    void bindNotification(Long deliveryId, Long notificationId, String deliveryStatus);

    void update(NotificationDelivery delivery);

    List<NotificationDelivery> findPendingDigestDeliveries(Long recipientId);
}
