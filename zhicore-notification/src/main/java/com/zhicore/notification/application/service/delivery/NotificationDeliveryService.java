package com.zhicore.notification.application.service.delivery;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.application.dto.NotificationDeliveryDTO;
import com.zhicore.notification.application.service.channel.ChannelDeliveryService;
import com.zhicore.notification.application.service.channel.NotificationPushDeliveryService;
import com.zhicore.notification.application.service.query.NotificationPreferenceQueryService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import com.zhicore.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private static final Set<String> RETRYABLE_STATUSES = Set.of(
            "PLANNED",
            "WEBSOCKET_PENDING",
            "FAILED",
            "SKIPPED"
    );

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPushDeliveryService notificationPushDeliveryService;
    private final NotificationPreferenceQueryService notificationPreferenceQueryService;

    public PageResult<NotificationDeliveryDTO> queryDeliveries(Long campaignId,
                                                               Long recipientId,
                                                               String channel,
                                                               String status,
                                                               int page,
                                                               int size) {
        List<NotificationDeliveryDTO> records = notificationDeliveryRepository.query(
                        campaignId,
                        recipientId,
                        channel,
                        status,
                        page,
                        size
                )
                .stream()
                .map(this::toDTO)
                .toList();
        long total = notificationDeliveryRepository.count(campaignId, recipientId, channel, status);
        return PageResult.of(page, size, total, records);
    }

    public void retryDelivery(Long deliveryId, Long requesterId, boolean admin) {
        NotificationDelivery delivery = notificationDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ResultCode.OPERATION_FAILED, "delivery不存在"));
        if (!admin && !delivery.getRecipientId().equals(requesterId)) {
            throw new BusinessException(ResultCode.RESOURCE_ACCESS_DENIED, "无权重试该delivery");
        }
        if (delivery.getChannel() != NotificationChannel.WEBSOCKET) {
            throw new BusinessException(ResultCode.OPERATION_FAILED, "仅支持重试WEBSOCKET通道");
        }
        if (!RETRYABLE_STATUSES.contains(delivery.getDeliveryStatus())) {
            throw new BusinessException(ResultCode.OPERATION_FAILED, "当前delivery不可重试");
        }
        if (delivery.getNotificationId() == null) {
            throw new BusinessException(ResultCode.OPERATION_FAILED, "delivery缺少通知引用");
        }

        Notification notification = notificationRepository.findById(delivery.getNotificationId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOTIFICATION_NOT_FOUND));

        boolean channelEnabled = notificationPreferenceQueryService.isChannelEnabled(
                delivery.getRecipientId(),
                notification.getType(),
                notification.getActorId(),
                NotificationChannel.WEBSOCKET,
                LocalTime.now()
        );
        if (!channelEnabled) {
            delivery.markSkipped("SKIPPED", "CHANNEL_DISABLED_OR_DND", notification.getId(), null);
            notificationDeliveryRepository.update(delivery);
            return;
        }

        ChannelDeliveryService.DeliveryResult result =
                notificationPushDeliveryService.deliver(delivery, notification);
        if (result.isSuccess()) {
            delivery.markSent(notification.getId());
        } else {
            delivery.markFailed(result.status(), result.reason(), Instant.now().plusSeconds(300));
        }
        notificationDeliveryRepository.update(delivery);
    }

    private NotificationDeliveryDTO toDTO(NotificationDelivery delivery) {
        return NotificationDeliveryDTO.builder()
                .id(delivery.getDeliveryId())
                .campaignId(delivery.getCampaignId())
                .recipientId(delivery.getRecipientId())
                .notificationId(delivery.getNotificationId())
                .channel(delivery.getChannel() != null ? delivery.getChannel().name() : null)
                .status(delivery.getDeliveryStatus())
                .skipReason(delivery.getSkipReason())
                .failureReason(delivery.getFailureReason())
                .retryCount(delivery.getRetryCount())
                .lastAttemptAt(delivery.getLastAttemptAt())
                .nextRetryAt(delivery.getNextRetryAt())
                .sentAt(delivery.getSentAt())
                .createdAt(delivery.getCreatedAt())
                .updatedAt(delivery.getUpdatedAt())
                .build();
    }
}
