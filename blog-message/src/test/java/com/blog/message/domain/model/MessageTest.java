package com.blog.message.domain.model;

import com.blog.common.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Message 聚合根单元测试
 *
 * @author Blog Team
 */
@DisplayName("Message 聚合根测试")
class MessageTest {

    private static final Long MESSAGE_ID = 1L;
    private static final Long CONVERSATION_ID = 100L;
    private static final Long SENDER_ID = 123L;
    private static final Long RECEIVER_ID = 456L;

    @Nested
    @DisplayName("创建文本消息")
    class CreateTextMessage {

        @Test
        @DisplayName("应该成功创建文本消息")
        void shouldCreateTextMessage() {
            String content = "Hello, World!";
            
            Message message = Message.createText(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, content);
            
            assertNotNull(message);
            assertEquals(MESSAGE_ID, message.getId());
            assertEquals(CONVERSATION_ID, message.getConversationId());
            assertEquals(SENDER_ID, message.getSenderId());
            assertEquals(RECEIVER_ID, message.getReceiverId());
            assertEquals(MessageType.TEXT, message.getType());
            assertEquals(content, message.getContent());
            assertNull(message.getMediaUrl());
            assertFalse(message.isRead());
            assertNull(message.getReadAt());
            assertEquals(MessageStatus.SENT, message.getStatus());
            assertNotNull(message.getCreatedAt());
        }

        @Test
        @DisplayName("空内容应该抛出异常")
        void shouldThrowExceptionForEmptyContent() {
            assertThrows(DomainException.class, () -> 
                    Message.createText(MESSAGE_ID, CONVERSATION_ID, SENDER_ID, RECEIVER_ID, ""));
            
            assertThrows(DomainException.class, () -> 
                    Message.createText(MESSAGE_ID, CONVERSATION_ID, SENDER_ID, RECEIVER_ID, "   "));
        }

        @Test
        @DisplayName("超长内容应该抛出异常")
        void shouldThrowExceptionForTooLongContent() {
            String longContent = "a".repeat(5001);
            
            assertThrows(DomainException.class, () -> 
                    Message.createText(MESSAGE_ID, CONVERSATION_ID, SENDER_ID, RECEIVER_ID, longContent));
        }

        @Test
        @DisplayName("5000字内容应该成功创建")
        void shouldCreateMessageWithMaxLengthContent() {
            String maxContent = "a".repeat(5000);
            
            Message message = Message.createText(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, maxContent);
            
            assertEquals(maxContent, message.getContent());
        }
    }

    @Nested
    @DisplayName("创建图片消息")
    class CreateImageMessage {

        @Test
        @DisplayName("应该成功创建图片消息")
        void shouldCreateImageMessage() {
            String imageUrl = "https://example.com/image.jpg";
            
            Message message = Message.createImage(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, imageUrl);
            
            assertNotNull(message);
            assertEquals(MessageType.IMAGE, message.getType());
            assertNull(message.getContent());
            assertEquals(imageUrl, message.getMediaUrl());
        }

        @Test
        @DisplayName("空图片URL应该抛出异常")
        void shouldThrowExceptionForEmptyImageUrl() {
            assertThrows(IllegalArgumentException.class, () -> 
                    Message.createImage(MESSAGE_ID, CONVERSATION_ID, SENDER_ID, RECEIVER_ID, ""));
        }
    }

    @Nested
    @DisplayName("创建文件消息")
    class CreateFileMessage {

        @Test
        @DisplayName("应该成功创建文件消息")
        void shouldCreateFileMessage() {
            String fileName = "document.pdf";
            String fileUrl = "https://example.com/document.pdf";
            
            Message message = Message.createFile(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, fileName, fileUrl);
            
            assertNotNull(message);
            assertEquals(MessageType.FILE, message.getType());
            assertEquals(fileName, message.getContent());
            assertEquals(fileUrl, message.getMediaUrl());
        }

        @Test
        @DisplayName("空文件URL应该抛出异常")
        void shouldThrowExceptionForEmptyFileUrl() {
            assertThrows(IllegalArgumentException.class, () -> 
                    Message.createFile(MESSAGE_ID, CONVERSATION_ID, SENDER_ID, RECEIVER_ID, "file.pdf", ""));
        }
    }

    @Nested
    @DisplayName("标记已读")
    class MarkAsRead {

        @Test
        @DisplayName("应该成功标记消息为已读")
        void shouldMarkMessageAsRead() {
            Message message = Message.createText(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, "Hello");
            
            assertFalse(message.isRead());
            assertNull(message.getReadAt());
            
            message.markAsRead();
            
            assertTrue(message.isRead());
            assertNotNull(message.getReadAt());
        }

        @Test
        @DisplayName("重复标记已读不应该更新时间")
        void shouldNotUpdateReadAtWhenAlreadyRead() {
            Message message = Message.createText(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, "Hello");
            
            message.markAsRead();
            LocalDateTime firstReadAt = message.getReadAt();
            
            // 等待一小段时间
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            message.markAsRead();
            
            assertEquals(firstReadAt, message.getReadAt());
        }
    }

    @Nested
    @DisplayName("撤回消息")
    class RecallMessage {

        @Test
        @DisplayName("发送者应该能撤回自己的消息")
        void shouldRecallOwnMessage() {
            Message message = Message.createText(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, "Hello");
            
            message.recall(SENDER_ID);
            
            assertEquals(MessageStatus.RECALLED, message.getStatus());
            assertEquals("[消息已撤回]", message.getContent());
            assertTrue(message.isRecalled());
        }

        @Test
        @DisplayName("非发送者不能撤回消息")
        void shouldNotAllowOthersToRecall() {
            Message message = Message.createText(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, "Hello");
            
            assertThrows(DomainException.class, () -> message.recall(RECEIVER_ID));
            assertThrows(DomainException.class, () -> message.recall(789L));
        }

        @Test
        @DisplayName("已撤回的消息不能再次撤回")
        void shouldNotRecallAlreadyRecalledMessage() {
            Message message = Message.createText(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, "Hello");
            
            message.recall(SENDER_ID);
            
            assertThrows(DomainException.class, () -> message.recall(SENDER_ID));
        }
    }

    @Nested
    @DisplayName("消息预览")
    class GetPreviewContent {

        @Test
        @DisplayName("文本消息应该返回截断的内容")
        void shouldReturnTruncatedTextContent() {
            String longContent = "a".repeat(200);
            Message message = Message.createText(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, longContent);
            
            String preview = message.getPreviewContent(100);
            
            assertEquals(103, preview.length()); // 100 + "..."
            assertTrue(preview.endsWith("..."));
        }

        @Test
        @DisplayName("图片消息应该返回[图片]")
        void shouldReturnImagePlaceholder() {
            Message message = Message.createImage(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, "https://example.com/image.jpg");
            
            assertEquals("[图片]", message.getPreviewContent(100));
        }

        @Test
        @DisplayName("文件消息应该返回[文件]加文件名")
        void shouldReturnFilePlaceholder() {
            Message message = Message.createFile(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, "document.pdf", "https://example.com/document.pdf");
            
            assertEquals("[文件] document.pdf", message.getPreviewContent(100));
        }

        @Test
        @DisplayName("已撤回消息应该返回[消息已撤回]")
        void shouldReturnRecalledPlaceholder() {
            Message message = Message.createText(MESSAGE_ID, CONVERSATION_ID, 
                    SENDER_ID, RECEIVER_ID, "Hello");
            message.recall(SENDER_ID);
            
            assertEquals("[消息已撤回]", message.getPreviewContent(100));
        }
    }

    @Nested
    @DisplayName("消息重建")
    class Reconstitute {

        @Test
        @DisplayName("应该正确重建消息")
        void shouldReconstituteMessage() {
            LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
            LocalDateTime readAt = LocalDateTime.now();
            
            Message message = Message.reconstitute(
                    MESSAGE_ID, CONVERSATION_ID, SENDER_ID, RECEIVER_ID,
                    MessageType.TEXT, "Hello", null,
                    true, readAt, MessageStatus.SENT, createdAt
            );
            
            assertEquals(MESSAGE_ID, message.getId());
            assertEquals(CONVERSATION_ID, message.getConversationId());
            assertEquals(SENDER_ID, message.getSenderId());
            assertEquals(RECEIVER_ID, message.getReceiverId());
            assertEquals(MessageType.TEXT, message.getType());
            assertEquals("Hello", message.getContent());
            assertTrue(message.isRead());
            assertEquals(readAt, message.getReadAt());
            assertEquals(MessageStatus.SENT, message.getStatus());
            assertEquals(createdAt, message.getCreatedAt());
        }
    }
}

