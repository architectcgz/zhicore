package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.integration.messaging.comment.CommentCreatedIntegrationEvent;
import com.zhicore.notification.application.dto.CommentStreamHintPayload;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.infrastructure.push.NotificationPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentCreatedNotificationConsumer 测试")
class CommentCreatedNotificationConsumerTest {

    @Mock
    private StatefulIdempotentHandler idempotentHandler;

    @Mock
    private NotificationCommandService notificationService;

    @Mock
    private NotificationPushService pushService;

    @Test
    @DisplayName("顶级评论时应该广播评论流提示并通知文章作者")
    void shouldBroadcastStreamHintAndNotifyPostOwner() {
        CommentCreatedNotificationConsumer consumer = new CommentCreatedNotificationConsumer(
                idempotentHandler, notificationService, pushService);
        CommentCreatedIntegrationEvent event = new CommentCreatedIntegrationEvent(
                "evt-1",
                Instant.parse("2026-03-27T10:00:00Z"),
                1001L,
                2002L,
                3003L,
                4004L,
                null,
                null,
                "新的顶级评论",
                null
        );
        Notification notification = Notification.createCommentNotification(1L, 3003L, 4004L, 2002L, 1001L, "新的顶级评论");

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        }).when(idempotentHandler).handleIdempotent(eq(event.getEventId()), any(Runnable.class));
        when(notificationService.createCommentNotificationIfAbsent(
                NotificationEventKeys.notificationId(event.getEventId(), "comment-owner"),
                3003L,
                4004L,
                2002L,
                1001L,
                "新的顶级评论"
        )).thenReturn(Optional.of(notification));

        consumer.onMessage(com.zhicore.common.util.JsonUtils.toJson(event));

        verify(pushService).broadcastCommentStreamHint(eq("2002"), any(CommentStreamHintPayload.class));
        verify(pushService).push("3003", notification);
        verify(notificationService, never()).createReplyNotificationIfAbsent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("回复评论时即使跳过通知也应该广播评论流提示")
    void shouldBroadcastStreamHintForReplyEvenWhenNotificationSkipped() {
        CommentCreatedNotificationConsumer consumer = new CommentCreatedNotificationConsumer(
                idempotentHandler, notificationService, pushService);
        CommentCreatedIntegrationEvent event = new CommentCreatedIntegrationEvent(
                "evt-2",
                Instant.parse("2026-03-27T10:05:00Z"),
                1002L,
                2002L,
                3003L,
                3003L,
                1001L,
                5005L,
                "新的回复",
                null
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        }).when(idempotentHandler).handleIdempotent(eq(event.getEventId()), any(Runnable.class));
        when(notificationService.createReplyNotificationIfAbsent(
                NotificationEventKeys.notificationId(event.getEventId(), "comment-reply"),
                5005L,
                3003L,
                1002L,
                "新的回复"
        )).thenReturn(Optional.empty());

        consumer.onMessage(com.zhicore.common.util.JsonUtils.toJson(event));

        verify(pushService).broadcastCommentStreamHint(eq("2002"), any(CommentStreamHintPayload.class));
        verify(notificationService).createReplyNotificationIfAbsent(
                NotificationEventKeys.notificationId(event.getEventId(), "comment-reply"),
                5005L,
                3003L,
                1002L,
                "新的回复"
        );
        verify(pushService, never()).push(eq("5005"), any(Notification.class));
    }
}
