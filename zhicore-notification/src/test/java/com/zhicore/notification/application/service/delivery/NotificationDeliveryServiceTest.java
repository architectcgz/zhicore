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
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import com.zhicore.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private NotificationPushDeliveryService notificationPushDeliveryService;

    @Mock
    private NotificationPreferenceQueryService notificationPreferenceQueryService;

    @Test
    @DisplayName("查询 delivery 时应返回分页 DTO")
    void queryDeliveries_shouldReturnPagedDtos() {
        NotificationDeliveryService service = new NotificationDeliveryService(
                notificationDeliveryRepository,
                notificationRepository,
                notificationPushDeliveryService,
                notificationPreferenceQueryService
        );
        NotificationDelivery delivery = NotificationDelivery.builder()
                .deliveryId(101L)
                .campaignId(201L)
                .recipientId(301L)
                .notificationId(401L)
                .channel(NotificationChannel.WEBSOCKET)
                .notificationType(NotificationType.POST_PUBLISHED_BY_FOLLOWING)
                .dedupeKey("dedupe-101")
                .deliveryStatus("FAILED")
                .failureReason("SOCKET_DOWN")
                .retryCount(2)
                .createdAt(Instant.parse("2026-03-27T10:00:00Z"))
                .updatedAt(Instant.parse("2026-03-27T10:05:00Z"))
                .lastAttemptAt(Instant.parse("2026-03-27T10:05:00Z"))
                .nextRetryAt(Instant.parse("2026-03-27T10:10:00Z"))
                .build();

        when(notificationDeliveryRepository.query(201L, 301L, "WEBSOCKET", "FAILED", 0, 20))
                .thenReturn(List.of(delivery));
        when(notificationDeliveryRepository.count(201L, 301L, "WEBSOCKET", "FAILED"))
                .thenReturn(1L);

        PageResult<NotificationDeliveryDTO> result =
                service.queryDeliveries(201L, 301L, "WEBSOCKET", "FAILED", 0, 20);

        assertEquals(1, result.getRecords().size());
        assertEquals("FAILED", result.getRecords().get(0).getStatus());
        assertEquals("SOCKET_DOWN", result.getRecords().get(0).getFailureReason());
    }

    @Test
    @DisplayName("重试 websocket delivery 成功时应更新为 SENT")
    void retryDelivery_shouldMarkSentWhenWebsocketDeliverySucceeds() {
        NotificationDeliveryService service = new NotificationDeliveryService(
                notificationDeliveryRepository,
                notificationRepository,
                notificationPushDeliveryService,
                notificationPreferenceQueryService
        );
        NotificationDelivery delivery = NotificationDelivery.builder()
                .deliveryId(101L)
                .campaignId(201L)
                .recipientId(301L)
                .notificationId(401L)
                .channel(NotificationChannel.WEBSOCKET)
                .notificationType(NotificationType.POST_PUBLISHED_BY_FOLLOWING)
                .dedupeKey("dedupe-101")
                .deliveryStatus("WEBSOCKET_PENDING")
                .createdAt(Instant.parse("2026-03-27T10:00:00Z"))
                .updatedAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();
        Notification notification = Notification.createPostPublishedNotification(
                401L,
                301L,
                901L,
                2001L,
                "group-1",
                "作者发布了新作品"
        );

        when(notificationDeliveryRepository.findById(101L)).thenReturn(Optional.of(delivery));
        when(notificationRepository.findById(401L)).thenReturn(Optional.of(notification));
        when(notificationPreferenceQueryService.isChannelEnabled(
                eq(301L),
                eq(NotificationType.POST_PUBLISHED_BY_FOLLOWING),
                eq(901L),
                eq(NotificationChannel.WEBSOCKET),
                any(LocalTime.class)
        )).thenReturn(true);
        when(notificationPushDeliveryService.deliver(delivery, notification))
                .thenReturn(ChannelDeliveryService.DeliveryResult.success("WEBSOCKET_DISPATCHED"));

        service.retryDelivery(101L, 301L, false);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository).update(captor.capture());
        assertEquals("SENT", captor.getValue().getDeliveryStatus());
        assertEquals(401L, captor.getValue().getNotificationId());
    }

    @Test
    @DisplayName("命中 DND 或渠道关闭时重试应标记为 skipped")
    void retryDelivery_shouldSkipWhenWebsocketChannelDisabled() {
        NotificationDeliveryService service = new NotificationDeliveryService(
                notificationDeliveryRepository,
                notificationRepository,
                notificationPushDeliveryService,
                notificationPreferenceQueryService
        );
        NotificationDelivery delivery = NotificationDelivery.builder()
                .deliveryId(101L)
                .campaignId(201L)
                .recipientId(301L)
                .notificationId(401L)
                .channel(NotificationChannel.WEBSOCKET)
                .notificationType(NotificationType.POST_PUBLISHED_BY_FOLLOWING)
                .dedupeKey("dedupe-101")
                .deliveryStatus("FAILED")
                .retryCount(1)
                .createdAt(Instant.parse("2026-03-27T10:00:00Z"))
                .updatedAt(Instant.parse("2026-03-27T10:05:00Z"))
                .build();
        Notification notification = Notification.createPostPublishedNotification(
                401L,
                301L,
                901L,
                2001L,
                "group-1",
                "作者发布了新作品"
        );

        when(notificationDeliveryRepository.findById(101L)).thenReturn(Optional.of(delivery));
        when(notificationRepository.findById(401L)).thenReturn(Optional.of(notification));
        when(notificationPreferenceQueryService.isChannelEnabled(
                eq(301L),
                eq(NotificationType.POST_PUBLISHED_BY_FOLLOWING),
                eq(901L),
                eq(NotificationChannel.WEBSOCKET),
                any(LocalTime.class)
        )).thenReturn(false);

        service.retryDelivery(101L, 301L, false);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository).update(captor.capture());
        assertEquals("SKIPPED", captor.getValue().getDeliveryStatus());
        assertEquals("CHANNEL_DISABLED_OR_DND", captor.getValue().getFailureReason());
    }

    @Test
    @DisplayName("非 websocket delivery 不允许重试")
    void retryDelivery_shouldRejectNonWebsocketDelivery() {
        NotificationDeliveryService service = new NotificationDeliveryService(
                notificationDeliveryRepository,
                notificationRepository,
                notificationPushDeliveryService,
                notificationPreferenceQueryService
        );
        NotificationDelivery delivery = NotificationDelivery.builder()
                .deliveryId(101L)
                .campaignId(201L)
                .recipientId(301L)
                .notificationId(401L)
                .channel(NotificationChannel.IN_APP)
                .notificationType(NotificationType.POST_PUBLISHED_BY_FOLLOWING)
                .dedupeKey("dedupe-101")
                .deliveryStatus("INBOX_CREATED")
                .createdAt(Instant.parse("2026-03-27T10:00:00Z"))
                .updatedAt(Instant.parse("2026-03-27T10:05:00Z"))
                .build();
        when(notificationDeliveryRepository.findById(101L)).thenReturn(Optional.of(delivery));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.retryDelivery(101L, 301L, false));

        assertEquals(ResultCode.OPERATION_FAILED.getCode(), exception.getCode());
    }
}
