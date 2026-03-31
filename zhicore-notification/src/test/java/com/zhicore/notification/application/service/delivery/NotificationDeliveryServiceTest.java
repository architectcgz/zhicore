package com.zhicore.notification.application.service.delivery;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.application.dto.NotificationDeliveryDTO;
import com.zhicore.notification.application.service.preference.NotificationPreferenceService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import com.zhicore.notification.domain.repository.NotificationRepository;
import com.zhicore.notification.infrastructure.push.NotificationPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDeliveryService 测试")
class NotificationDeliveryServiceTest {

    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPushService notificationPushService;

    @Mock
    private NotificationPreferenceService notificationPreferenceService;

    @Test
    @DisplayName("查询 delivery 时应返回分页 DTO")
    void queryDeliveries_shouldReturnPagedDtos() {
        NotificationDeliveryService service = new NotificationDeliveryService(
                notificationDeliveryRepository,
                notificationRepository,
                notificationPushService,
                notificationPreferenceService
        );
        NotificationDelivery delivery = NotificationDelivery.reconstitute(
                101L,
                201L,
                202L,
                301L,
                "PUSH",
                "dedupe-101",
                com.zhicore.notification.domain.model.NotificationDeliveryStatus.FAILED,
                401L,
                null,
                "PUSH_DELIVERY_FAILED",
                2,
                OffsetDateTime.parse("2026-03-27T18:05:00+08:00"),
                OffsetDateTime.parse("2026-03-27T18:10:00+08:00"),
                OffsetDateTime.parse("2026-03-27T18:00:00+08:00"),
                OffsetDateTime.parse("2026-03-27T18:05:00+08:00"),
                null
        );

        when(notificationDeliveryRepository.query(201L, 301L, "PUSH", "FAILED", 0, 20))
                .thenReturn(List.of(delivery));
        when(notificationDeliveryRepository.count(201L, 301L, "PUSH", "FAILED"))
                .thenReturn(1L);

        PageResult<NotificationDeliveryDTO> result =
                service.queryDeliveries(201L, 301L, "PUSH", "FAILED", 0, 20);

        assertEquals(1, result.getRecords().size());
        assertEquals("FAILED", result.getRecords().get(0).getStatus());
        assertEquals("PUSH_DELIVERY_FAILED", result.getRecords().get(0).getFailureReason());
    }

    @Test
    @DisplayName("重试 PUSH delivery 成功时应更新为 SENT")
    void retryDelivery_shouldMarkSentWhenPushSucceeds() {
        NotificationDeliveryService service = new NotificationDeliveryService(
                notificationDeliveryRepository,
                notificationRepository,
                notificationPushService,
                notificationPreferenceService
        );
        NotificationDelivery delivery = NotificationDelivery.reconstitute(
                101L,
                201L,
                202L,
                301L,
                "PUSH",
                "dedupe-101",
                com.zhicore.notification.domain.model.NotificationDeliveryStatus.FAILED,
                401L,
                null,
                "PUSH_DELIVERY_FAILED",
                1,
                OffsetDateTime.parse("2026-03-27T18:05:00+08:00"),
                OffsetDateTime.parse("2026-03-27T18:10:00+08:00"),
                OffsetDateTime.parse("2026-03-27T18:00:00+08:00"),
                OffsetDateTime.parse("2026-03-27T18:05:00+08:00"),
                null
        );
        Notification notification = Notification.createPostPublishedNotification(
                401L,
                301L,
                901L,
                2001L,
                5001L
        );

        when(notificationDeliveryRepository.findById(101L)).thenReturn(Optional.of(delivery));
        when(notificationRepository.findById(401L)).thenReturn(Optional.of(notification));
        when(notificationPreferenceService.isPreferenceEnabled(301L, NotificationType.POST_PUBLISHED)).thenReturn(true);
        when(notificationPreferenceService.isDndActive(301L)).thenReturn(false);
        when(notificationPushService.push("301", notification)).thenReturn(true);

        service.retryDelivery(101L, 301L, false);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository).update(captor.capture());
        assertEquals(com.zhicore.notification.domain.model.NotificationDeliveryStatus.SENT, captor.getValue().getStatus());
        assertEquals(401L, captor.getValue().getNotificationId());
    }

    @Test
    @DisplayName("命中 DND 或渠道关闭时重试应标记为 skipped")
    void retryDelivery_shouldSkipWhenPushChannelDisabled() {
        NotificationDeliveryService service = new NotificationDeliveryService(
                notificationDeliveryRepository,
                notificationRepository,
                notificationPushService,
                notificationPreferenceService
        );
        NotificationDelivery delivery = NotificationDelivery.reconstitute(
                101L,
                201L,
                202L,
                301L,
                "PUSH",
                "dedupe-101",
                com.zhicore.notification.domain.model.NotificationDeliveryStatus.FAILED,
                401L,
                null,
                "PUSH_DELIVERY_FAILED",
                1,
                OffsetDateTime.parse("2026-03-27T18:05:00+08:00"),
                OffsetDateTime.parse("2026-03-27T18:10:00+08:00"),
                OffsetDateTime.parse("2026-03-27T18:00:00+08:00"),
                OffsetDateTime.parse("2026-03-27T18:05:00+08:00"),
                null
        );
        Notification notification = Notification.createPostPublishedNotification(
                401L,
                301L,
                901L,
                2001L,
                5001L
        );

        when(notificationDeliveryRepository.findById(101L)).thenReturn(Optional.of(delivery));
        when(notificationRepository.findById(401L)).thenReturn(Optional.of(notification));
        when(notificationPreferenceService.isPreferenceEnabled(301L, NotificationType.POST_PUBLISHED)).thenReturn(false);

        service.retryDelivery(101L, 301L, false);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository).update(captor.capture());
        assertEquals(com.zhicore.notification.domain.model.NotificationDeliveryStatus.SKIPPED, captor.getValue().getStatus());
        assertEquals("CHANNEL_DISABLED_OR_DND", captor.getValue().getSkipReason());
    }

    @Test
    @DisplayName("非 PUSH delivery 不允许重试")
    void retryDelivery_shouldRejectNonPushDelivery() {
        NotificationDeliveryService service = new NotificationDeliveryService(
                notificationDeliveryRepository,
                notificationRepository,
                notificationPushService,
                notificationPreferenceService
        );
        NotificationDelivery delivery = NotificationDelivery.pending(
                101L, 201L, 202L, 301L, "INBOX", "dedupe-101");
        when(notificationDeliveryRepository.findById(101L)).thenReturn(Optional.of(delivery));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.retryDelivery(101L, 301L, false));

        assertEquals(ResultCode.OPERATION_FAILED.getCode(), exception.getCode());
    }
}
