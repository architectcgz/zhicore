package com.zhicore.notification.infrastructure.mq;

import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.infrastructure.mq.payload.UserNotificationFanoutEvent;
import com.zhicore.notification.infrastructure.push.WebSocketNotificationHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserNotificationFanoutConsumer 测试")
class UserNotificationFanoutConsumerTest {

    @Mock
    private WebSocketNotificationHandler webSocketNotificationHandler;

    @InjectMocks
    private UserNotificationFanoutConsumer consumer;

    @Test
    @DisplayName("应该把用户通知 fanout 事件转发到本机 WebSocket")
    void shouldForwardUserNotificationToLocalWebSocket() {
        AggregatedNotificationVO payload = AggregatedNotificationVO.builder()
                .type(NotificationType.FOLLOW)
                .targetType("USER")
                .targetId(202L)
                .totalCount(1)
                .unreadCount(1)
                .latestTime(OffsetDateTime.parse("2026-03-27T10:00:00+08:00"))
                .latestContent("关注了你")
                .aggregatedContent("alice关注了你")
                .build();
        UserNotificationFanoutEvent event = UserNotificationFanoutEvent.builder()
                .eventId("evt-2")
                .userId("202")
                .payload(payload)
                .build();

        consumer.onMessage(com.zhicore.common.util.JsonUtils.toJson(event));

        verify(webSocketNotificationHandler).sendNotification(
                org.mockito.ArgumentMatchers.eq("202"),
                argThat(actual -> actual != null
                        && actual.getType() == NotificationType.FOLLOW
                        && "USER".equals(actual.getTargetType())
                        && Long.valueOf(202L).equals(actual.getTargetId())
                        && actual.getTotalCount() == 1
                        && actual.getUnreadCount() == 1
                        && "关注了你".equals(actual.getLatestContent())
                        && "alice关注了你".equals(actual.getAggregatedContent())
                        && actual.getLatestTime() != null
                        && actual.getLatestTime().toInstant().equals(payload.getLatestTime().toInstant()))
        );
    }
}
