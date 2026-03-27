package com.zhicore.notification.domain.repository;

import com.zhicore.notification.domain.model.NotificationDelivery;

import java.util.Optional;

public interface NotificationDeliveryRepository {

    boolean saveIfAbsent(NotificationDelivery delivery);

    Optional<NotificationDelivery> findByDedupeKey(String dedupeKey);

    void update(NotificationDelivery delivery);
}
