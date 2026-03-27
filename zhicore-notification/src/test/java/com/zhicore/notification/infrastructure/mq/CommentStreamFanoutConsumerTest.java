package com.zhicore.notification.infrastructure.mq;

import com.zhicore.notification.application.dto.CommentStreamHintPayload;
import com.zhicore.notification.infrastructure.mq.payload.CommentStreamFanoutEvent;
import com.zhicore.notification.infrastructure.push.WebSocketNotificationHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentStreamFanoutConsumer 测试")
class CommentStreamFanoutConsumerTest {

    @Mock
    private WebSocketNotificationHandler webSocketNotificationHandler;

    @InjectMocks
    private CommentStreamFanoutConsumer consumer;

    @Test
    @DisplayName("应该把评论流 fanout 事件转发到本机 WebSocket")
    void shouldForwardCommentStreamHintToLocalWebSocket() {
        CommentStreamHintPayload payload = CommentStreamHintPayload.builder()
                .eventId("evt-1")
                .eventType("COMMENT_CREATED")
                .postId(2002L)
                .commentId(3003L)
                .parentId(4004L)
                .occurredAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();
        CommentStreamFanoutEvent event = CommentStreamFanoutEvent.builder()
                .eventId("evt-1")
                .postId("2002")
                .payload(payload)
                .build();

        consumer.onMessage(com.zhicore.common.util.JsonUtils.toJson(event));

        verify(webSocketNotificationHandler).sendCommentStreamHint("2002", payload);
    }
}
