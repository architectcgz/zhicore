package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.BestEffortNotificationPublisher;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.dto.CommentStreamHintPayload;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.infrastructure.mq.payload.CommentStreamFanoutEvent;
import com.zhicore.notification.infrastructure.mq.payload.UnreadCountFanoutEvent;
import com.zhicore.notification.infrastructure.mq.payload.UserNotificationFanoutEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRealtimeFanoutPublisher 测试")
class NotificationRealtimeFanoutPublisherTest {

    @Mock
    private BestEffortNotificationPublisher bestEffortNotificationPublisher;

    @InjectMocks
    private NotificationRealtimeFanoutPublisher publisher;

    @Captor
    private ArgumentCaptor<Object> notificationCaptor;

    @Captor
    private ArgumentCaptor<String> tagCaptor;

    @Captor
    private ArgumentCaptor<String> hashKeyCaptor;

    @Test
    @DisplayName("应该按文章维度顺序发布评论流 fanout 事件")
    void shouldPublishCommentStreamHintOrderly() {
        CommentStreamHintPayload payload = CommentStreamHintPayload.builder()
                .eventId("evt-1")
                .eventType("COMMENT_CREATED")
                .postId(202L)
                .commentId(303L)
                .parentId(404L)
                .occurredAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();

        publisher.publishCommentStreamHint("202", payload);

        verify(bestEffortNotificationPublisher).publishOrderlyAsync(
                eq(TopicConstants.TOPIC_NOTIFICATION_EVENTS),
                eq(TopicConstants.TAG_NOTIFICATION_REALTIME_COMMENT_STREAM),
                notificationCaptor.capture(),
                hashKeyCaptor.capture()
        );

        CommentStreamFanoutEvent event = (CommentStreamFanoutEvent) notificationCaptor.getValue();
        assertEquals("evt-1", event.getEventId());
        assertEquals("202", event.getPostId());
        assertEquals(payload, event.getPayload());
        assertEquals("202", hashKeyCaptor.getValue());
    }

    @Test
    @DisplayName("应该按用户维度顺序发布通知和未读数 fanout 事件")
    void shouldPublishUserNotificationAndUnreadCountOrderly() {
        AggregatedNotificationVO payload = AggregatedNotificationVO.builder()
                .type(NotificationType.FOLLOW)
                .targetType("USER")
                .targetId(202L)
                .totalCount(2)
                .unreadCount(1)
                .latestTime(OffsetDateTime.parse("2026-03-27T10:00:00+08:00"))
                .latestContent("关注了你")
                .aggregatedContent("alice等2人关注了你")
                .build();

        publisher.publishUserNotification("202", payload, 4);

        verify(bestEffortNotificationPublisher, times(2)).publishOrderlyAsync(
                org.mockito.ArgumentMatchers.eq(TopicConstants.TOPIC_NOTIFICATION_EVENTS),
                tagCaptor.capture(),
                notificationCaptor.capture(),
                hashKeyCaptor.capture()
        );

        assertEquals(2, tagCaptor.getAllValues().size());
        assertEquals(TopicConstants.TAG_NOTIFICATION_REALTIME_USER_NOTIFICATION, tagCaptor.getAllValues().get(0));
        assertEquals(TopicConstants.TAG_NOTIFICATION_REALTIME_UNREAD_COUNT, tagCaptor.getAllValues().get(1));
        assertEquals("202", hashKeyCaptor.getAllValues().get(0));
        assertEquals("202", hashKeyCaptor.getAllValues().get(1));

        UserNotificationFanoutEvent userEvent = (UserNotificationFanoutEvent) notificationCaptor.getAllValues().get(0);
        assertEquals("202", userEvent.getUserId());
        assertEquals(payload, userEvent.getPayload());
        assertTrue(userEvent.getEventId().contains("follow:user:202"));

        UnreadCountFanoutEvent unreadEvent = (UnreadCountFanoutEvent) notificationCaptor.getAllValues().get(1);
        assertEquals("202", unreadEvent.getUserId());
        assertEquals(4, unreadEvent.getUnreadCount());
        assertEquals(userEvent.getEventId() + ":unread", unreadEvent.getEventId());
    }

    @Test
    @DisplayName("应该支持单独发布未读数 fanout 事件")
    void shouldPublishUnreadCountOnly() {
        publisher.publishUnreadCount("202", 7);

        verify(bestEffortNotificationPublisher).publishOrderlyAsync(
                eq(TopicConstants.TOPIC_NOTIFICATION_EVENTS),
                eq(TopicConstants.TAG_NOTIFICATION_REALTIME_UNREAD_COUNT),
                notificationCaptor.capture(),
                hashKeyCaptor.capture()
        );

        UnreadCountFanoutEvent event = (UnreadCountFanoutEvent) notificationCaptor.getValue();
        assertEquals("202", event.getUserId());
        assertEquals(7, event.getUnreadCount());
        assertEquals("unread:202:7:unread", event.getEventId());
        assertEquals("202", hashKeyCaptor.getValue());
    }
}
