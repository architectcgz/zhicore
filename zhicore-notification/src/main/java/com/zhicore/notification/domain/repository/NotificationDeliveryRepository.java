package com.zhicore.notification.domain.repository;

import com.zhicore.notification.domain.model.NotificationDelivery;

import java.util.List;

public interface NotificationDeliveryRepository {

    boolean saveIfAbsent(NotificationDelivery delivery);

    void bindNotification(Long deliveryId, Long notificationId, String deliveryStatus);

    List<NotificationDelivery> findPendingDigestDeliveries(Long recipientId);
}
