package com.zhicore.notification.application.service.digest;

import com.zhicore.notification.application.service.channel.ChannelDeliveryService;
import com.zhicore.notification.application.service.channel.NotificationEmailDeliveryService;
import com.zhicore.notification.application.service.channel.NotificationInboxDeliveryService;
import com.zhicore.notification.application.service.channel.NotificationPushDeliveryService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.model.NotificationDigestJob;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationDigestService {

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationInboxDeliveryService notificationInboxDeliveryService;
    private final NotificationPushDeliveryService notificationPushDeliveryService;
    private final NotificationEmailDeliveryService notificationEmailDeliveryService;

    @Transactional
    public DigestResult processPendingDigest(Long recipientId) {
        List<NotificationDelivery> pendingDeliveries =
                notificationDeliveryRepository.findPendingDigestDeliveries(recipientId);
        if (pendingDeliveries.isEmpty()) {
            return DigestResult.empty();
        }

        NotificationDigestJob digestJob = NotificationDigestJob.fromDeliveries(recipientId, pendingDeliveries);
        Notification digestNotification = notificationInboxDeliveryService.deliverDigestSummary(
                digestJob.getRecipientId(),
                digestJob.getGroupKey(),
                digestJob.getContent()
        );

        for (NotificationDelivery delivery : pendingDeliveries) {
            notificationDeliveryRepository.bindNotification(
                    delivery.getDeliveryId(),
                    digestNotification.getId(),
                    "DIGEST_DELIVERED"
            );
        }

        ChannelDeliveryService.DeliveryResult pushResult =
                notificationPushDeliveryService.deliver(pendingDeliveries.get(0), digestNotification);
        ChannelDeliveryService.DeliveryResult emailResult =
                notificationEmailDeliveryService.deliver(pendingDeliveries.get(0), digestNotification);

        return DigestResult.builder()
                .deliveryCount(pendingDeliveries.size())
                .notificationId(digestNotification.getId())
                .pushStatus(pushResult.status())
                .emailStatus(emailResult.status())
                .build();
    }

    @Getter
    @Builder
    public static class DigestResult {
        private final int deliveryCount;
        private final Long notificationId;
        private final String pushStatus;
        private final String emailStatus;

        public static DigestResult empty() {
            return DigestResult.builder()
                    .deliveryCount(0)
                    .pushStatus("NO_PENDING_DIGEST")
                    .emailStatus("NO_PENDING_DIGEST")
                    .build();
        }
    }
}
