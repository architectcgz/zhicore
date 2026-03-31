package com.zhicore.notification.application.service.channel;

import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.NotificationDelivery;

public interface ChannelDeliveryService {

    NotificationChannel channel();

    DeliveryResult deliver(NotificationDelivery delivery, Notification notification);

    record DeliveryResult(String status, String reason) {

        public static DeliveryResult success(String status) {
            return new DeliveryResult(status, null);
        }

        public static DeliveryResult failure(String status, String reason) {
            return new DeliveryResult(status, reason);
        }

        public static DeliveryResult skipped(String status, String reason) {
            return new DeliveryResult(status, reason);
        }

        public boolean isSuccess() {
            return reason == null;
        }
    }
}
