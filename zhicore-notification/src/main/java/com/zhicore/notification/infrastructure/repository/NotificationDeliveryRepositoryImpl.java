package com.zhicore.notification.infrastructure.repository;

import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationDeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationDeliveryRepositoryImpl implements NotificationDeliveryRepository {

    private final NotificationDeliveryMapper notificationDeliveryMapper;

    @Override
    public boolean saveIfAbsent(NotificationDelivery delivery) {
        return notificationDeliveryMapper.insertIgnore(delivery) > 0;
    }

    @Override
    public void bindNotification(Long deliveryId, Long notificationId, String deliveryStatus) {
        notificationDeliveryMapper.bindNotification(deliveryId, notificationId, deliveryStatus);
    }

    @Override
    public List<NotificationDelivery> findPendingDigestDeliveries(Long recipientId) {
        return notificationDeliveryMapper.findPendingDigestDeliveries(recipientId);
    }
}
