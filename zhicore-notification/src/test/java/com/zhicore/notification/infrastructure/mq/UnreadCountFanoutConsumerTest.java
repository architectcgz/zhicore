package com.zhicore.notification.infrastructure.mq;

import com.zhicore.notification.infrastructure.mq.payload.UnreadCountFanoutEvent;
import com.zhicore.notification.infrastructure.push.WebSocketNotificationHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnreadCountFanoutConsumer 测试")
class UnreadCountFanoutConsumerTest {

    @Mock
    private WebSocketNotificationHandler webSocketNotificationHandler;

    @InjectMocks
    private UnreadCountFanoutConsumer consumer;

    @Test
    @DisplayName("应该把未读数 fanout 事件转发到本机 WebSocket")
    void shouldForwardUnreadCountToLocalWebSocket() {
        UnreadCountFanoutEvent event = UnreadCountFanoutEvent.builder()
                .eventId("evt-3")
                .userId("202")
                .unreadCount(7)
                .build();

        consumer.onMessage(com.zhicore.common.util.JsonUtils.toJson(event));

        verify(webSocketNotificationHandler).sendUnreadCount("202", 7);
    }
}
