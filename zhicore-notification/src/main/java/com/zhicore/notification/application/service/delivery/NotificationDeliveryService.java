package com.zhicore.notification.application.service.delivery;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.application.dto.NotificationDeliveryDTO;
import com.zhicore.notification.application.service.preference.NotificationPreferenceService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.model.NotificationDeliveryStatus;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import com.zhicore.notification.domain.repository.NotificationRepository;
import com.zhicore.notification.infrastructure.push.NotificationPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private static final String CHANNEL_PUSH = "PUSH";
    private static final String SKIP_CHANNEL_DISABLED_OR_DND = "CHANNEL_DISABLED_OR_DND";
    private static final String FAILURE_PUSH_DELIVERY = "PUSH_DELIVERY_FAILED";
    private static final Set<NotificationDeliveryStatus> RETRYABLE_STATUSES = Set.of(
            NotificationDeliveryStatus.PENDING,
            NotificationDeliveryStatus.FAILED,
            NotificationDeliveryStatus.SKIPPED
    );

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPushService notificationPushService;
    private final NotificationPreferenceService notificationPreferenceService;

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
        if (!CHANNEL_PUSH.equals(delivery.getChannel())) {
            throw new BusinessException(ResultCode.OPERATION_FAILED, "仅支持重试PUSH通道");
        }
        if (!RETRYABLE_STATUSES.contains(delivery.getStatus())) {
            throw new BusinessException(ResultCode.OPERATION_FAILED, "当前delivery不可重试");
        }
        if (delivery.getNotificationId() == null) {
            throw new BusinessException(ResultCode.OPERATION_FAILED, "delivery缺少通知引用");
        }

        Notification notification = notificationRepository.findById(delivery.getNotificationId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOTIFICATION_NOT_FOUND));

        if (!notificationPreferenceService.isPreferenceEnabled(delivery.getRecipientId(), notification.getType())
                || notificationPreferenceService.isDndActive(delivery.getRecipientId())) {
            delivery.markSkipped(SKIP_CHANNEL_DISABLED_OR_DND, notification.getId(), null);
            notificationDeliveryRepository.update(delivery);
            return;
        }

        if (notificationPushService.push(String.valueOf(delivery.getRecipientId()), notification)) {
            delivery.markSent(notification.getId());
        } else {
            delivery.markFailed(FAILURE_PUSH_DELIVERY, OffsetDateTime.now().plusMinutes(5), notification.getId());
        }
        notificationDeliveryRepository.update(delivery);
    }

    private NotificationDeliveryDTO toDTO(NotificationDelivery delivery) {
        return NotificationDeliveryDTO.builder()
                .id(delivery.getId())
                .campaignId(delivery.getCampaignId())
                .recipientId(delivery.getRecipientId())
                .notificationId(delivery.getNotificationId())
                .channel(delivery.getChannel())
                .status(delivery.getStatus().name())
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
