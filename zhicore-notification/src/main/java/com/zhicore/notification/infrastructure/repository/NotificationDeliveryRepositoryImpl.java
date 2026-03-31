package com.zhicore.notification.infrastructure.repository;

import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import com.zhicore.notification.infrastructure.repository.mapper.NotificationDeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationDeliveryRepositoryImpl implements NotificationDeliveryRepository {

    private final NotificationDeliveryMapper notificationDeliveryMapper;

    @Override
    public boolean saveIfAbsent(NotificationDelivery delivery) {
        return notificationDeliveryMapper.insertIgnore(delivery) > 0;
    }

    @Override
    public Optional<NotificationDelivery> findById(Long deliveryId) {
        return Optional.ofNullable(notificationDeliveryMapper.findById(deliveryId));
    }

    @Override
    public List<NotificationDelivery> query(Long campaignId,
                                            Long recipientId,
                                            String channel,
                                            String status,
                                            int page,
                                            int size) {
        return notificationDeliveryMapper.query(campaignId, recipientId, channel, status, size, (long) page * size);
    }

    @Override
    public long count(Long campaignId, Long recipientId, String channel, String status) {
        return notificationDeliveryMapper.count(campaignId, recipientId, channel, status);
    }

    @Override
    public void bindNotification(Long deliveryId, Long notificationId, String deliveryStatus) {
        notificationDeliveryMapper.bindNotification(deliveryId, notificationId, deliveryStatus);
    }

    @Override
    public void update(NotificationDelivery delivery) {
        notificationDeliveryMapper.updateState(delivery);
    }

    @Override
    public List<NotificationDelivery> findPendingDigestDeliveries(Long recipientId) {
        return notificationDeliveryMapper.findPendingDigestDeliveries(recipientId);
    }
}
