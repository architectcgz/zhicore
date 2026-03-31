package com.zhicore.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Notification 聚合根单元测试
 *
 * @author ZhiCore Team
 */
@DisplayName("Notification 聚合根测试")
class NotificationTest {

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTests {

        @Test
        @DisplayName("创建点赞通知 - 成功")
        void createLikeNotification_Success() {
            // Given
            Long id = 1L;
            Long recipientId = 123L;
            Long actorId = 456L;
            String targetType = "post";
            Long targetId = 100L;

            // When
            Notification notification = Notification.createLikeNotification(
                    id, recipientId, actorId, targetType, targetId);

            // Then
            assertNotNull(notification);
            assertEquals(id, notification.getId());
            assertEquals(recipientId, notification.getRecipientId());
            assertEquals(actorId, notification.getActorId());
            assertEquals(NotificationType.LIKE, notification.getType());
            assertEquals("INTERACTION", notification.getCategory());
            assertEquals("interaction.like", notification.getEventCode());
            assertEquals(targetType, notification.getTargetType());
            assertEquals(targetId, notification.getTargetId());
            assertEquals("赞了你的文章", notification.getContent());
            assertFalse(notification.isRead());
            assertNull(notification.getReadAt());
            assertNotNull(notification.getCreatedAt());
        }

        @Test
        @DisplayName("创建点赞通知 - 评论类型")
        void createLikeNotification_CommentType() {
            // When
            Notification notification = Notification.createLikeNotification(
                    1L, 123L, 456L, "comment", 200L);

            // Then
            assertEquals("赞了你的评论", notification.getContent());
        }

        @Test
        @DisplayName("创建点赞通知 - 参数校验失败")
        void createLikeNotification_ValidationFailed() {
            assertThrows(IllegalArgumentException.class, () ->
                    Notification.createLikeNotification(null, 123L, 456L, "post", 100L));

            assertThrows(IllegalArgumentException.class, () ->
                    Notification.createLikeNotification(1L, null, 456L, "post", 100L));

            assertThrows(IllegalArgumentException.class, () ->
                    Notification.createLikeNotification(1L, 123L, null, "post", 100L));
        }

        @Test
        @DisplayName("创建评论通知 - 成功")
        void createCommentNotification_Success() {
            // Given
            String commentContent = "这是一条评论内容";

            // When
            Notification notification = Notification.createCommentNotification(
                    1L, 123L, 456L, 100L, 200L, commentContent);

            // Then
            assertEquals(NotificationType.COMMENT, notification.getType());
            assertEquals("post", notification.getTargetType());
            assertEquals(100L, notification.getTargetId());
            assertEquals(commentContent, notification.getContent());
        }

        @Test
        @DisplayName("创建评论通知 - 内容截断")
        void createCommentNotification_ContentTruncated() {
            // Given
            String longContent = "这是一条非常长的评论内容".repeat(20);

            // When
            Notification notification = Notification.createCommentNotification(
                    1L, 123L, 456L, 100L, 200L, longContent);

            // Then
            assertTrue(notification.getContent().length() <= 103); // 100 + "..."
            assertTrue(notification.getContent().endsWith("..."));
        }

        @Test
        @DisplayName("创建回复通知 - 成功")
        void createReplyNotification_Success() {
            // When
            Notification notification = Notification.createReplyNotification(
                    1L, 123L, 456L, 200L, "回复内容");

            // Then
            assertEquals(NotificationType.REPLY, notification.getType());
            assertEquals("comment", notification.getTargetType());
            assertEquals(200L, notification.getTargetId());
        }

        @Test
        @DisplayName("创建关注通知 - 成功")
        void createFollowNotification_Success() {
            // When
            Notification notification = Notification.createFollowNotification(
                    1L, 123L, 456L);

            // Then
            assertEquals(NotificationType.FOLLOW, notification.getType());
            assertEquals(456L, notification.getActorId());
            assertEquals("关注了你", notification.getContent());
            assertNull(notification.getTargetType());
            assertNull(notification.getTargetId());
        }

        @Test
        @DisplayName("创建系统通知 - 成功")
        void createSystemNotification_Success() {
            // When
            Notification notification = Notification.createSystemNotification(
                    1L, 123L, "系统维护通知");

            // Then
            assertEquals(NotificationType.SYSTEM, notification.getType());
            assertNull(notification.getActorId());
            assertEquals("系统维护通知", notification.getContent());
        }

        @Test
        @DisplayName("创建关注作者发文通知 - 应设置分类和分组键")
        void createPostPublishedNotification_shouldSetCategoryAndGroupKey() {
            Notification notification = Notification.createPostPublishedNotification(
                    1L, 200L, 100L, 300L, "post_publish:100:300", "author published");

            assertEquals(NotificationType.POST_PUBLISHED_BY_FOLLOWING, notification.getType());
            assertEquals(NotificationCategory.CONTENT, notification.getCategoryEnum());
            assertEquals("post_publish:100:300", notification.getGroupKey());
        }
    }

    @Nested
    @DisplayName("领域行为测试")
    class DomainBehaviorTests {

        @Test
        @DisplayName("标记已读 - 成功")
        void markAsRead_Success() {
            // Given
            Notification notification = Notification.createFollowNotification(
                    1L, 123L, 456L);
            assertFalse(notification.isRead());

            // When
            notification.markAsRead();

            // Then
            assertTrue(notification.isRead());
            assertNotNull(notification.getReadAt());
        }

        @Test
        @DisplayName("标记已读 - 幂等性")
        void markAsRead_Idempotent() {
            // Given
            Notification notification = Notification.createFollowNotification(
                    1L, 123L, 456L);
            notification.markAsRead();
            OffsetDateTime firstReadAt = notification.getReadAt();

            // When
            notification.markAsRead();

            // Then
            assertEquals(firstReadAt, notification.getReadAt());
        }

        @Test
        @DisplayName("同一聚合组判断 - 相同类型和目标")
        void isSameGroup_SameTypeAndTarget() {
            // Given
            Notification n1 = Notification.createLikeNotification(
                    1L, 123L, 456L, "post", 100L);
            Notification n2 = Notification.createLikeNotification(
                    2L, 123L, 789L, "post", 100L);

            // Then
            assertTrue(n1.isSameGroup(n2));
        }

        @Test
        @DisplayName("同一聚合组判断 - 不同类型")
        void isSameGroup_DifferentType() {
            // Given
            Notification n1 = Notification.createLikeNotification(
                    1L, 123L, 456L, "post", 100L);
            Notification n2 = Notification.createCommentNotification(
                    2L, 123L, 789L, 100L, 200L, "评论");

            // Then
            assertFalse(n1.isSameGroup(n2));
        }

        @Test
        @DisplayName("同一聚合组判断 - 不同目标")
        void isSameGroup_DifferentTarget() {
            // Given
            Notification n1 = Notification.createLikeNotification(
                    1L, 123L, 456L, "post", 100L);
            Notification n2 = Notification.createLikeNotification(
                    2L, 123L, 789L, "post", 200L);

            // Then
            assertFalse(n1.isSameGroup(n2));
        }

        @Test
        @DisplayName("同一聚合组判断 - 关注通知总是同组")
        void isSameGroup_FollowAlwaysSameGroup() {
            // Given
            Notification n1 = Notification.createFollowNotification(
                    1L, 123L, 456L);
            Notification n2 = Notification.createFollowNotification(
                    2L, 123L, 789L);

            // Then
            assertTrue(n1.isSameGroup(n2));
        }

        @Test
        @DisplayName("同一聚合组判断 - null 参数")
        void isSameGroup_NullParameter() {
            // Given
            Notification n1 = Notification.createFollowNotification(
                    1L, 123L, 456L);

            // Then
            assertFalse(n1.isSameGroup(null));
        }
    }

    @Nested
    @DisplayName("重建测试")
    class ReconstituteTests {

        @Test
        @DisplayName("从持久化重建 - 成功")
        void reconstitute_Success() {
            // Given
            Long id = 1L;
            Long recipientId = 123L;
            NotificationType type = NotificationType.LIKE;
            Long actorId = 456L;
            String targetType = "post";
            Long targetId = 100L;
            String content = "赞了你的文章";
            boolean isRead = true;
            OffsetDateTime readAt = OffsetDateTime.now().minusHours(1);
            OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);

            // When
            Notification notification = Notification.reconstitute(
                    id, recipientId, type, actorId, targetType, targetId,
                    content, isRead, readAt, createdAt);

            // Then
            assertEquals(id, notification.getId());
            assertEquals(recipientId, notification.getRecipientId());
            assertEquals(type, notification.getType());
            assertEquals(actorId, notification.getActorId());
            assertEquals(targetType, notification.getTargetType());
            assertEquals(targetId, notification.getTargetId());
            assertEquals(content, notification.getContent());
            assertTrue(notification.isRead());
            assertEquals(readAt, notification.getReadAt());
            assertEquals(createdAt, notification.getCreatedAt());
        }

        @Test
        @DisplayName("从持久化重建 - legacy 空 eventCode 应回退到类型默认事件编码")
        void reconstitute_ShouldFallbackEventCodeWhenLegacyEventCodeIsBlank() {
            OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);

            Notification notification = Notification.reconstitute(
                    1L, 123L, NotificationType.SYSTEM,
                    "SYSTEM", " ", null,
                    null, null, null,
                    "系统通知", false, null, createdAt);

            assertEquals("system.notice", notification.getEventCode());
        }

        @Test
        @DisplayName("从持久化重建 - legacy 哨兵 eventCode 应回退到类型默认事件编码")
        void reconstitute_ShouldFallbackEventCodeWhenLegacySentinelUsed() {
            OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);

            Notification notification = Notification.reconstitute(
                    1L, 123L, NotificationType.LIKE,
                    "INTERACTION", "__legacy__", null,
                    456L, "post", 100L,
                    "赞了你的文章", false, null, createdAt);

            assertEquals("interaction.like", notification.getEventCode());
            assertEquals("INTERACTION", notification.getCategory());
        }
    }
}
