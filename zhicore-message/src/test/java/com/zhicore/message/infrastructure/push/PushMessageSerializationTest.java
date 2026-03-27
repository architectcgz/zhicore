package com.zhicore.message.infrastructure.push;

import com.zhicore.common.util.JsonUtils;
import com.zhicore.message.domain.model.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("PushMessage JSON 序列化回归测试")
class PushMessageSerializationTest {

    @Test
    @DisplayName("PushMessage 应该可以稳定完成 JSON 往返")
    void shouldRoundTripPushMessageJson() {
        PushMessage message = PushMessage.builder()
                .messageId(101L)
                .conversationId(202L)
                .senderId(303L)
                .senderNickName("sender")
                .senderAvatarUrl("avatar.png")
                .type(MessageType.TEXT)
                .contentPreview("hello")
                .sentAt(LocalDateTime.of(2026, 3, 23, 12, 30))
                .pushType(PushMessage.PushType.NEW_MESSAGE)
                .build();

        PushMessage restored = assertDoesNotThrow(() ->
                JsonUtils.fromJson(JsonUtils.toJson(message), PushMessage.class));

        assertEquals(message.getMessageId(), restored.getMessageId());
        assertEquals(message.getConversationId(), restored.getConversationId());
        assertEquals(message.getSenderId(), restored.getSenderId());
        assertEquals(message.getSenderNickName(), restored.getSenderNickName());
        assertEquals(message.getSenderAvatarUrl(), restored.getSenderAvatarUrl());
        assertEquals(message.getType(), restored.getType());
        assertEquals(message.getContentPreview(), restored.getContentPreview());
        assertEquals(message.getSentAt(), restored.getSentAt());
        assertEquals(message.getPushType(), restored.getPushType());
    }
}
