package com.zhicore.notification.infrastructure.push;

import com.zhicore.notification.application.dto.CommentStreamHintPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketNotificationHandler 测试")
class WebSocketNotificationHandlerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketNotificationHandler webSocketNotificationHandler;

    @Test
    @DisplayName("应该向文章评论流 topic 广播提示")
    void shouldBroadcastCommentStreamHint() {
        CommentStreamHintPayload payload = CommentStreamHintPayload.builder()
                .eventId("evt-1")
                .eventType("COMMENT_CREATED")
                .postId(2002L)
                .commentId(1001L)
                .parentId(3003L)
                .occurredAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();

        webSocketNotificationHandler.sendCommentStreamHint("2002", payload);

        verify(messagingTemplate).convertAndSend("/topic/posts/2002/comment-stream", payload);
    }
}
