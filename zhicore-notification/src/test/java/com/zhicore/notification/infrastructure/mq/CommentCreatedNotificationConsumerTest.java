package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.integration.messaging.comment.CommentCreatedIntegrationEvent;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.infrastructure.push.NotificationPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @DisplayName("评论事件应保留私有通知并追加评论流 hint 广播")
    void shouldKeepPrivateNotificationsAndBroadcastHint() {
        CommentCreatedNotificationConsumer consumer = new CommentCreatedNotificationConsumer(
                idempotentHandler, notificationService, pushService);
        CommentCreatedIntegrationEvent event = new CommentCreatedIntegrationEvent(
                "evt-1",
                Instant.parse("2026-03-31T08:00:00Z"),
                1001L,
                2002L,
                3003L,
                4004L,
                5005L,
                5005L,
                6006L,
                "reply content",
                null
        );
        Notification ownerNotification = Notification.createCommentNotification(11L, 3003L, 4004L, 2002L, 1001L, "reply content");
        Notification replyNotification = Notification.createReplyNotification(12L, 6006L, 4004L, 1001L, "reply content");

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        }).when(idempotentHandler).handleIdempotent(eq(event.getEventId()), any(Runnable.class));
        when(notificationService.createCommentNotificationIfAbsent(anyLong(), eq(3003L), eq(4004L), eq(2002L), eq(1001L), eq("reply content")))
                .thenReturn(Optional.of(ownerNotification));
        when(notificationService.createReplyNotificationIfAbsent(anyLong(), eq(6006L), eq(4004L), eq(1001L), eq("reply content")))
                .thenReturn(Optional.of(replyNotification));

        consumer.onMessage(JsonUtils.toJson(event));

        verify(pushService).push("3003", ownerNotification);
        verify(pushService).push("6006", replyNotification);
        verify(pushService).broadcastPostCommentStreamHint(eq(2002L), eq(Map.of(
                "eventId", "evt-1",
                "eventType", "created",
                "occurredAt", Instant.parse("2026-03-31T08:00:00Z"),
                "postId", 2002L,
                "commentId", 1001L,
                "parentId", 5005L,
                "rootId", 5005L
        )));
    }

    @Test
    @DisplayName("作者自评时不发私有通知但仍广播评论流 hint")
    void shouldOnlyBroadcastHintWhenNoPrivateNotificationNeeded() {
        CommentCreatedNotificationConsumer consumer = new CommentCreatedNotificationConsumer(
                idempotentHandler, notificationService, pushService);
        CommentCreatedIntegrationEvent event = new CommentCreatedIntegrationEvent(
                "evt-2",
                Instant.parse("2026-03-31T08:01:00Z"),
                1002L,
                2003L,
                4004L,
                4004L,
                null,
                1002L,
                null,
                "self comment",
                null
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        }).when(idempotentHandler).handleIdempotent(eq(event.getEventId()), any(Runnable.class));

        consumer.onMessage(JsonUtils.toJson(event));

        verify(notificationService, never()).createCommentNotificationIfAbsent(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any());
        verify(notificationService, never()).createReplyNotificationIfAbsent(anyLong(), anyLong(), anyLong(), anyLong(), any());
        verify(pushService, never()).push(any(), any(Notification.class));
        verify(pushService).broadcastPostCommentStreamHint(eq(2003L), any(Map.class));
    }
}
