package com.zhicore.notification.infrastructure.push;

import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.service.NotificationAggregationService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationRepository;
import com.zhicore.notification.infrastructure.mq.NotificationRealtimeFanoutPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationPushService 测试")
class NotificationPushServiceTest {

    @Mock
    private NotificationRealtimeFanoutPublisher fanoutPublisher;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationAggregationService notificationAggregationService;

    private NotificationPushService notificationPushService;

    @BeforeEach
    void setUp() {
        notificationPushService = new NotificationPushService(
                fanoutPublisher,
                notificationRepository,
                notificationAggregationService
        );
    }

    @Test
    @DisplayName("推送通知时应该发布 realtime fanout 事件")
    void push_shouldPublishRealtimeFanout() {
        Notification notification = Notification.createPostPublishedDigestNotification(
                9001L, 11L, "author_publish_digest:11:2026-03-26", "你关注的作者有 2 篇新作品更新"
        );
        AggregatedNotificationVO aggregated = AggregatedNotificationVO.builder()
                .type(NotificationType.POST_PUBLISHED_DIGEST)
                .latestTime(OffsetDateTime.parse("2026-03-27T10:00:00+08:00"))
                .build();
        when(notificationAggregationService.getAggregatedNotificationForPush(notification)).thenReturn(aggregated);
        when(notificationRepository.countUnread(11L)).thenReturn(3);

        boolean pushed = notificationPushService.push("11", notification);

        verify(notificationAggregationService).getAggregatedNotificationForPush(notification);
        verify(notificationRepository).countUnread(11L);
        verify(fanoutPublisher).publishUserNotification("11", aggregated, 3);
        assertTrue(pushed);
    }

    @Test
    @DisplayName("实时 fanout 发布异常时不应抛出错误")
    void push_shouldSwallowRealtimeFanoutException() {
        Notification notification = Notification.createPostPublishedDigestNotification(
                9002L, 11L, "author_publish_digest:11:2026-03-26", "你关注的作者有 2 篇新作品更新"
        );
        AggregatedNotificationVO aggregated = AggregatedNotificationVO.builder()
                .type(NotificationType.POST_PUBLISHED_DIGEST)
                .latestTime(OffsetDateTime.parse("2026-03-27T10:00:00+08:00"))
                .build();
        when(notificationAggregationService.getAggregatedNotificationForPush(notification)).thenReturn(aggregated);
        when(notificationRepository.countUnread(11L)).thenReturn(3);
        doThrow(new IllegalStateException("mq down"))
                .when(fanoutPublisher)
                .publishUserNotification("11", aggregated, 3);

        boolean pushed = notificationPushService.push("11", notification);

        verify(fanoutPublisher).publishUserNotification("11", aggregated, 3);
        assertFalse(pushed);
    }
}
