package com.zhicore.notification.application.service.channel;

import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.infrastructure.push.NotificationPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationPushDeliveryService implements ChannelDeliveryService {

    private final NotificationPushService notificationPushService;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WEBSOCKET;
    }

    @Override
    public DeliveryResult deliver(NotificationDelivery delivery, Notification notification) {
        if (notification == null) {
            return DeliveryResult.skipped("SKIPPED_MISSING_NOTIFICATION", "MISSING_NOTIFICATION");
        }
        notificationPushService.push(String.valueOf(delivery.getRecipientId()), notification);
        return DeliveryResult.success("PUSH_DISPATCHED");
    }
}
