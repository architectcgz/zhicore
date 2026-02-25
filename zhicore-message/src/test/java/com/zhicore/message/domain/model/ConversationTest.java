package com.zhicore.message.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conversation 聚合根单元测试
 *
 * @author ZhiCore Team
 */
@DisplayName("Conversation 聚合根测试")
class ConversationTest {

    private static final Long CONVERSATION_ID = 1L;
    private static final Long USER_A = 123L;
    private static final Long USER_B = 456L;

    @Nested
    @DisplayName("创建会话")
    class CreateConversation {

        @Test
        @DisplayName("应该成功创建会话并规范化参与者ID")
        void shouldCreateConversationWithNormalizedParticipants() {
            // USER_A < USER_B 按字典序
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            assertNotNull(conversation);
            assertEquals(CONVERSATION_ID, conversation.getId());
            assertEquals(USER_A, conversation.getParticipant1Id());
            assertEquals(USER_B, conversation.getParticipant2Id());
            assertEquals(0, conversation.getUnreadCount1());
            assertEquals(0, conversation.getUnreadCount2());
            assertNotNull(conversation.getCreatedAt());
        }

        @Test
        @DisplayName("参与者顺序颠倒时应该自动规范化")
        void shouldNormalizeParticipantsOrder() {
            // 传入顺序颠倒
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_B, USER_A);
            
            // 应该自动规范化为 USER_A < USER_B
            assertEquals(USER_A, conversation.getParticipant1Id());
            assertEquals(USER_B, conversation.getParticipant2Id());
        }

        @Test
        @DisplayName("不能与自己创建会话")
        void shouldNotCreateConversationWithSelf() {
            assertThrows(IllegalArgumentException.class, () -> 
                    Conversation.create(CONVERSATION_ID, USER_A, USER_A));
        }

        @Test
        @DisplayName("空用户ID应该抛出异常")
        void shouldThrowExceptionForEmptyUserId() {
            assertThrows(IllegalArgumentException.class, () -> 
                    Conversation.create(CONVERSATION_ID, null, USER_B));
            
            assertThrows(IllegalArgumentException.class, () -> 
                    Conversation.create(CONVERSATION_ID, USER_A, null));
        }
    }

    @Nested
    @DisplayName("更新最后消息")
    class UpdateLastMessage {

        @Test
        @DisplayName("应该更新最后消息并增加对方未读数")
        void shouldUpdateLastMessageAndIncrementUnreadCount() {
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            // USER_A 发送消息
            Message message = Message.createText(1L, CONVERSATION_ID, USER_A, USER_B, "Hello");
            conversation.updateLastMessage(message);
            
            assertEquals(message.getId(), conversation.getLastMessageId());
            assertEquals("Hello", conversation.getLastMessageContent());
            assertEquals(message.getCreatedAt(), conversation.getLastMessageAt());
            
            // USER_A 是 participant1，所以 USER_B (participant2) 的未读数增加
            assertEquals(0, conversation.getUnreadCount1());
            assertEquals(1, conversation.getUnreadCount2());
        }

        @Test
        @DisplayName("多次发送消息应该累加未读数")
        void shouldAccumulateUnreadCount() {
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            // USER_A 发送3条消息
            for (int i = 1; i <= 3; i++) {
                Message message = Message.createText((long) i, CONVERSATION_ID, USER_A, USER_B, "Message " + i);
                conversation.updateLastMessage(message);
            }
            
            assertEquals(0, conversation.getUnreadCount1());
            assertEquals(3, conversation.getUnreadCount2());
        }

        @Test
        @DisplayName("双方互发消息应该分别累加未读数")
        void shouldAccumulateUnreadCountForBothParties() {
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            // USER_A 发送2条消息
            conversation.updateLastMessage(Message.createText(1L, CONVERSATION_ID, USER_A, USER_B, "Hi"));
            conversation.updateLastMessage(Message.createText(2L, CONVERSATION_ID, USER_A, USER_B, "Hello"));
            
            // USER_B 发送1条消息
            conversation.updateLastMessage(Message.createText(3L, CONVERSATION_ID, USER_B, USER_A, "Hey"));
            
            assertEquals(1, conversation.getUnreadCount1()); // USER_A 有1条未读
            assertEquals(2, conversation.getUnreadCount2()); // USER_B 有2条未读
        }
    }

    @Nested
    @DisplayName("清除未读数")
    class ClearUnreadCount {

        @Test
        @DisplayName("应该清除指定用户的未读数")
        void shouldClearUnreadCountForUser() {
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            // USER_A 发送消息
            conversation.updateLastMessage(Message.createText(1L, CONVERSATION_ID, USER_A, USER_B, "Hello"));
            conversation.updateLastMessage(Message.createText(2L, CONVERSATION_ID, USER_A, USER_B, "World"));
            
            assertEquals(2, conversation.getUnreadCount2());
            
            // USER_B 清除未读
            conversation.clearUnreadCount(USER_B);
            
            assertEquals(0, conversation.getUnreadCount2());
            assertEquals(0, conversation.getUnreadCount1()); // USER_A 的未读数不变
        }

        @Test
        @DisplayName("清除不存在的用户未读数不应该报错")
        void shouldNotThrowExceptionForUnknownUser() {
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            // 清除不存在的用户未读数
            assertDoesNotThrow(() -> conversation.clearUnreadCount(999L));
        }
    }

    @Nested
    @DisplayName("获取对方参与者")
    class GetOtherParticipant {

        @Test
        @DisplayName("应该返回对方用户ID")
        void shouldReturnOtherParticipantId() {
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            assertEquals(USER_B, conversation.getOtherParticipant(USER_A));
            assertEquals(USER_A, conversation.getOtherParticipant(USER_B));
        }

        @Test
        @DisplayName("非参与者应该抛出异常")
        void shouldThrowExceptionForNonParticipant() {
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            assertThrows(IllegalArgumentException.class, () -> 
                    conversation.getOtherParticipant(999L));
        }
    }

    @Nested
    @DisplayName("获取未读数")
    class GetUnreadCount {

        @Test
        @DisplayName("应该返回指定用户的未读数")
        void shouldReturnUnreadCountForUser() {
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            // USER_A 发送消息
            conversation.updateLastMessage(Message.createText(1L, CONVERSATION_ID, USER_A, USER_B, "Hello"));
            
            assertEquals(0, conversation.getUnreadCount(USER_A));
            assertEquals(1, conversation.getUnreadCount(USER_B));
        }

        @Test
        @DisplayName("非参与者应该返回0")
        void shouldReturnZeroForNonParticipant() {
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            assertEquals(0, conversation.getUnreadCount(999L));
        }
    }

    @Nested
    @DisplayName("检查参与者")
    class IsParticipant {

        @Test
        @DisplayName("参与者应该返回true")
        void shouldReturnTrueForParticipant() {
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            assertTrue(conversation.isParticipant(USER_A));
            assertTrue(conversation.isParticipant(USER_B));
        }

        @Test
        @DisplayName("非参与者应该返回false")
        void shouldReturnFalseForNonParticipant() {
            Conversation conversation = Conversation.create(CONVERSATION_ID, USER_A, USER_B);
            
            assertFalse(conversation.isParticipant(999L));
        }
    }

    @Nested
    @DisplayName("规范化参与者ID")
    class NormalizeParticipants {

        @Test
        @DisplayName("应该按字典序排列参与者ID")
        void shouldNormalizeParticipantsInLexicographicOrder() {
            Long[] result1 = Conversation.normalizeParticipants(USER_A, USER_B);
            assertEquals(USER_A, result1[0]);
            assertEquals(USER_B, result1[1]);
            
            Long[] result2 = Conversation.normalizeParticipants(USER_B, USER_A);
            assertEquals(USER_A, result2[0]);
            assertEquals(USER_B, result2[1]);
        }

        @Test
        @DisplayName("相同用户ID应该保持顺序")
        void shouldMaintainOrderForSameIds() {
            Long[] result = Conversation.normalizeParticipants(1L, 2L);
            assertEquals(1L, result[0]);
            assertEquals(2L, result[1]);
        }
    }

    @Nested
    @DisplayName("会话重建")
    class Reconstitute {

        @Test
        @DisplayName("应该正确重建会话")
        void shouldReconstituteConversation() {
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
            LocalDateTime lastMessageAt = LocalDateTime.now();
            
            Conversation conversation = Conversation.reconstitute(
                    CONVERSATION_ID, USER_A, USER_B,
                    100L, "Last message", lastMessageAt,
                    5, 3, createdAt
            );
            
            assertEquals(CONVERSATION_ID, conversation.getId());
            assertEquals(USER_A, conversation.getParticipant1Id());
            assertEquals(USER_B, conversation.getParticipant2Id());
            assertEquals(100L, conversation.getLastMessageId());
            assertEquals("Last message", conversation.getLastMessageContent());
            assertEquals(lastMessageAt, conversation.getLastMessageAt());
            assertEquals(5, conversation.getUnreadCount1());
            assertEquals(3, conversation.getUnreadCount2());
            assertEquals(createdAt, conversation.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("会话唯一性测试")
    class ConversationUniqueness {

        @Test
        @DisplayName("相同参与者创建的会话应该有相同的规范化ID")
        void shouldHaveSameNormalizedParticipantsForSameUsers() {
            Conversation conv1 = Conversation.create(1L, USER_A, USER_B);
            Conversation conv2 = Conversation.create(2L, USER_B, USER_A);
            
            // 两个会话的参与者应该相同（规范化后）
            assertEquals(conv1.getParticipant1Id(), conv2.getParticipant1Id());
            assertEquals(conv1.getParticipant2Id(), conv2.getParticipant2Id());
        }
    }
}
