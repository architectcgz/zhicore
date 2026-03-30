package com.zhicore.notification.infrastructure.push;

import com.zhicore.notification.domain.model.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationPushService 测试")
class NotificationPushServiceTest {

    @Mock
    private WebSocketNotificationHandler webSocketNotificationHandler;

    private NotificationPushService notificationPushService;

    @BeforeEach
    void setUp() {
        notificationPushService = new NotificationPushService(webSocketNotificationHandler);
    }

    @Test
    @DisplayName("推送通知时应该调用 WebSocket handler")
    void push_shouldDelegateToWebSocketHandler() {
        Notification notification = Notification.createPostPublishedDigestNotification(
                9001L, 11L, "author_publish_digest:11:2026-03-26", "你关注的作者有 2 篇新作品更新"
        );

        boolean pushed = notificationPushService.push("11", notification);

        verify(webSocketNotificationHandler).sendNotification("11", notification);
        assertTrue(pushed);
    }

    @Test
    @DisplayName("WebSocket 推送异常时不应抛出错误")
    void push_shouldSwallowWebSocketException() {
        Notification notification = Notification.createPostPublishedDigestNotification(
                9002L, 11L, "author_publish_digest:11:2026-03-26", "你关注的作者有 2 篇新作品更新"
        );
        doThrow(new IllegalStateException("ws down"))
                .when(webSocketNotificationHandler)
                .sendNotification("11", notification);

        boolean pushed = notificationPushService.push("11", notification);

        verify(webSocketNotificationHandler).sendNotification("11", notification);
        assertFalse(pushed);
    }
}
