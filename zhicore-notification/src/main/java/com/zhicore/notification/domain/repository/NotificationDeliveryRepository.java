package com.zhicore.notification.domain.repository;

import com.zhicore.notification.domain.model.NotificationDelivery;

import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryRepository {

    boolean saveIfAbsent(NotificationDelivery delivery);

    Optional<NotificationDelivery> findById(Long deliveryId);

    Optional<NotificationDelivery> findByDedupeKey(String dedupeKey);

    List<NotificationDelivery> query(Long campaignId,
                                     Long recipientId,
                                     String channel,
                                     String status,
                                     int page,
                                     int size);

    long count(Long campaignId, Long recipientId, String channel, String status);

    void update(NotificationDelivery delivery);
}
