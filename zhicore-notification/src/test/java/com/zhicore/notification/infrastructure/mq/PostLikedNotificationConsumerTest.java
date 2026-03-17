package com.zhicore.notification.infrastructure.mq;

import com.zhicore.api.event.post.PostLikedEvent;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.infrastructure.push.NotificationPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostLikedNotificationConsumer 测试")
class PostLikedNotificationConsumerTest {

    @Mock
    private StatefulIdempotentHandler idempotentHandler;

    @Mock
    private NotificationCommandService notificationService;

    @Mock
    private NotificationPushService pushService;

    @Test
    @DisplayName("首次消费时应该创建并推送通知")
    void shouldCreateAndPushNotification() {
        PostLikedNotificationConsumer consumer = new PostLikedNotificationConsumer(
                idempotentHandler, notificationService, pushService);
        PostLikedEvent event = new PostLikedEvent(101L, 202L, 303L);
        Notification notification = Notification.createLikeNotification(1L, 303L, 202L, "post", 101L);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        }).when(idempotentHandler).handleIdempotent(eq(event.getEventId()), any(Runnable.class));
        when(notificationService.createLikeNotificationIfAbsent(anyLong(), eq(303L), eq(202L), eq("post"), eq(101L)))
                .thenReturn(Optional.of(notification));

        consumer.onMessage(com.zhicore.common.util.JsonUtils.toJson(event));

        verify(notificationService).createLikeNotificationIfAbsent(anyLong(), eq(303L), eq(202L), eq("post"), eq(101L));
        verify(pushService).push("303", notification);
    }

    @Test
    @DisplayName("重复消费时不应重复推送通知")
    void shouldSkipPushWhenNotificationAlreadyExists() {
        PostLikedNotificationConsumer consumer = new PostLikedNotificationConsumer(
                idempotentHandler, notificationService, pushService);
        PostLikedEvent event = new PostLikedEvent(101L, 202L, 303L);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        }).when(idempotentHandler).handleIdempotent(eq(event.getEventId()), any(Runnable.class));
        when(notificationService.createLikeNotificationIfAbsent(anyLong(), eq(303L), eq(202L), eq("post"), eq(101L)))
                .thenReturn(Optional.empty());

        consumer.onMessage(com.zhicore.common.util.JsonUtils.toJson(event));

        verify(notificationService).createLikeNotificationIfAbsent(anyLong(), eq(303L), eq(202L), eq("post"), eq(101L));
        verify(pushService, never()).push(any(), any(Notification.class));
    }
}
