package com.zhicore.notification.infrastructure.push;

import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.dto.CommentStreamHintPayload;
import com.zhicore.notification.application.service.NotificationAggregationService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationPushService 测试")
class NotificationPushServiceTest {

    @Mock
    private WebSocketNotificationHandler webSocketHandler;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationAggregationService notificationAggregationService;

    @InjectMocks
    private NotificationPushService notificationPushService;

    @Test
    @DisplayName("推送通知时应该同步推送未读数")
    void shouldPushUnreadCountAfterNotification() {
        Notification notification = Notification.createFollowNotification(1L, 202L, 303L);
        AggregatedNotificationVO aggregatedNotification = AggregatedNotificationVO.builder()
                .type(NotificationType.FOLLOW)
                .targetType("USER")
                .targetId(202L)
                .totalCount(2)
                .unreadCount(1)
                .latestTime(LocalDateTime.of(2026, 3, 24, 10, 0))
                .latestContent("关注了你")
                .aggregatedContent("alice等2人关注了你")
                .build();

        when(notificationAggregationService.getAggregatedNotificationForPush(notification))
                .thenReturn(aggregatedNotification);
        when(notificationRepository.countUnread(202L)).thenReturn(4);

        notificationPushService.push("202", notification);

        verify(notificationAggregationService).getAggregatedNotificationForPush(notification);
        verify(webSocketHandler).sendNotification("202", aggregatedNotification);
        verify(notificationRepository).countUnread(202L);
        verify(webSocketHandler).sendUnreadCount("202", 4);
    }

    @Test
    @DisplayName("广播评论流提示时应该委托给 WebSocket handler")
    void shouldBroadcastCommentStreamHint() {
        CommentStreamHintPayload payload = CommentStreamHintPayload.builder()
                .eventId("evt-1")
                .eventType("COMMENT_CREATED")
                .postId(202L)
                .commentId(303L)
                .parentId(404L)
                .occurredAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();

        notificationPushService.broadcastCommentStreamHint("202", payload);

        verify(webSocketHandler).sendCommentStreamHint("202", payload);
    }
}
