package com.zhicore.notification.application.service.channel;

import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.NotificationDelivery;
import org.springframework.stereotype.Service;

@Service
public class NotificationSmsDeliveryService implements ChannelDeliveryService {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public DeliveryResult deliver(NotificationDelivery delivery, Notification notification) {
        return DeliveryResult.skipped("SKIPPED_UNCONFIGURED", "SMS_PROVIDER_UNCONFIGURED");
    }
}
