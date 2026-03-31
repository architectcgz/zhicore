package com.zhicore.notification.application.service.digest;

import com.zhicore.notification.application.service.channel.NotificationEmailDeliveryService;
import com.zhicore.notification.application.service.channel.NotificationInboxDeliveryService;
import com.zhicore.notification.application.service.channel.NotificationPushDeliveryService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationDelivery;
import com.zhicore.notification.domain.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDigestService 测试")
class NotificationDigestServiceTest {

    @Mock
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Mock
    private NotificationInboxDeliveryService notificationInboxDeliveryService;

    @Mock
    private NotificationPushDeliveryService notificationPushDeliveryService;

    @Mock
    private NotificationEmailDeliveryService notificationEmailDeliveryService;

    private NotificationDigestService notificationDigestService;

    @BeforeEach
    void setUp() {
        notificationDigestService = new NotificationDigestService(
                notificationDeliveryRepository,
                notificationInboxDeliveryService,
                notificationPushDeliveryService,
                notificationEmailDeliveryService
        );
    }

    @Test
    @DisplayName("quiet hours 累积的发文通知应该合并成一条 digest 站内通知")
    void scheduleDigest_shouldAccumulatePublishNotificationsDuringQuietHours() {
        NotificationDelivery delivery1 = NotificationDelivery.digestPending(
                1001L, 11L, 7001L, "d1", "AUTHOR_DIGEST_ONLY"
        );
        NotificationDelivery delivery2 = NotificationDelivery.digestPending(
                1002L, 11L, 7002L, "d2", "AUTHOR_DIGEST_ONLY"
        );
        Notification digestNotification = Notification.createPostPublishedDigestNotification(
                9001L, 11L, "author_publish_digest:11:2026-03-26", "你关注的作者有 2 篇新作品更新"
        );

        when(notificationDeliveryRepository.findPendingDigestDeliveries(11L)).thenReturn(List.of(delivery1, delivery2));
        when(notificationInboxDeliveryService.deliverDigestSummary(any(), any(), any())).thenReturn(digestNotification);
        when(notificationPushDeliveryService.deliver(any(), any())).thenReturn(
                new com.zhicore.notification.application.service.channel.ChannelDeliveryService.DeliveryResult("PUSH_DISPATCHED", null)
        );
        when(notificationEmailDeliveryService.deliver(any(), any())).thenReturn(
                new com.zhicore.notification.application.service.channel.ChannelDeliveryService.DeliveryResult("SKIPPED_UNCONFIGURED", "EMAIL_PROVIDER_UNCONFIGURED")
        );

        NotificationDigestService.DigestResult result = notificationDigestService.processPendingDigest(11L);

        assertEquals(2, result.getDeliveryCount());
        assertEquals(9001L, result.getNotificationId());
        assertEquals("PUSH_DISPATCHED", result.getPushStatus());
        assertEquals("SKIPPED_UNCONFIGURED", result.getEmailStatus());
        verify(notificationInboxDeliveryService).deliverDigestSummary(11L, "author_publish_digest:11:" + java.time.LocalDate.now(), "你关注的作者有 2 篇新作品更新");
        verify(notificationDeliveryRepository, times(2)).bindNotification(any(), eq(9001L), eq("DIGEST_DELIVERED"));
    }
}
