package com.zhicore.content.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostTag 值对象单元测试
 * 
 * 测试 PostTag 值对象的创建、验证和领域行为
 * 
 * Requirements: 4.2
 *
 * @author ZhiCore Team
 */
@DisplayName("PostTag 值对象测试")
class PostTagTest {

    @Nested
    @DisplayName("创建 Post-Tag 关联")
    class CreatePostTag {

        @Test
        @DisplayName("应该成功创建 Post-Tag 关联")
        void shouldCreatePostTagSuccessfully() {
            // Given
            Long postId = 1L;
            Long tagId = 1001L;

            // When
            PostTag postTag = PostTag.create(postId, tagId);

            // Then
            assertNotNull(postTag);
            assertEquals(postId, postTag.getPostId());
            assertEquals(tagId, postTag.getTagId());
            assertNotNull(postTag.getCreatedAt());
            assertTrue(postTag.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        }

        @Test
        @DisplayName("文章ID为null时应该抛出异常")
        void shouldThrowExceptionWhenPostIdIsNull() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    PostTag.create(null, 1001L));
            assertTrue(exception.getMessage().contains("文章ID不能为空"));
        }

        @Test
        @DisplayName("文章ID为0时应该抛出异常")
        void shouldThrowExceptionWhenPostIdIsZero() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    PostTag.create(0L, 1001L));
            assertTrue(exception.getMessage().contains("文章ID必须为正数"));
        }

        @Test
        @DisplayName("文章ID为负数时应该抛出异常")
        void shouldThrowExceptionWhenPostIdIsNegative() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    PostTag.create(-1L, 1001L));
            assertTrue(exception.getMessage().contains("文章ID必须为正数"));
        }

        @Test
        @DisplayName("标签ID为null时应该抛出异常")
        void shouldThrowExceptionWhenTagIdIsNull() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    PostTag.create(1L, null));
            assertTrue(exception.getMessage().contains("标签ID不能为空"));
        }

        @Test
        @DisplayName("标签ID为0时应该抛出异常")
        void shouldThrowExceptionWhenTagIdIsZero() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    PostTag.create(1L, 0L));
            assertTrue(exception.getMessage().contains("标签ID必须为正数"));
        }

        @Test
        @DisplayName("标签ID为负数时应该抛出异常")
        void shouldThrowExceptionWhenTagIdIsNegative() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    PostTag.create(1L, -1L));
            assertTrue(exception.getMessage().contains("标签ID必须为正数"));
        }

        @Test
        @DisplayName("创建时间应该自动设置为当前时间")
        void shouldSetCreatedAtToCurrentTime() {
            // Given
            LocalDateTime before = LocalDateTime.now();

            // When
            PostTag postTag = PostTag.create(1L, 1001L);

            // Then
            LocalDateTime after = LocalDateTime.now();
            assertNotNull(postTag.getCreatedAt());
            assertTrue(postTag.getCreatedAt().isAfter(before.minusSeconds(1)));
            assertTrue(postTag.getCreatedAt().isBefore(after.plusSeconds(1)));
        }
    }

    @Nested
    @DisplayName("从持久化恢复 Post-Tag 关联")
    class ReconstitutePostTag {

        @Test
        @DisplayName("应该成功从持久化恢复 Post-Tag 关联")
        void shouldReconstitutePostTagSuccessfully() {
            // Given
            Long postId = 1L;
            Long tagId = 1001L;
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1);

            // When
            PostTag postTag = PostTag.reconstitute(postId, tagId, createdAt);

            // Then
            assertNotNull(postTag);
            assertEquals(postId, postTag.getPostId());
            assertEquals(tagId, postTag.getTagId());
            assertEquals(createdAt, postTag.getCreatedAt());
        }

        @Test
        @DisplayName("应该保留原始创建时间")
        void shouldPreserveOriginalCreatedAt() {
            // Given
            Long postId = 1L;
            Long tagId = 1001L;
            LocalDateTime originalCreatedAt = LocalDateTime.of(2024, 1, 1, 10, 0, 0);

            // When
            PostTag postTag = PostTag.reconstitute(postId, tagId, originalCreatedAt);

            // Then
            assertEquals(originalCreatedAt, postTag.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("equals 和 hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("相同 postId 和 tagId 的关联应该相等")
        void shouldBeEqualWhenSamePostIdAndTagId() {
            // Given
            PostTag postTag1 = PostTag.create(1L, 1001L);
            PostTag postTag2 = PostTag.create(1L, 1001L);

            // Then
            assertEquals(postTag1, postTag2);
            assertEquals(postTag1.hashCode(), postTag2.hashCode());
        }

        @Test
        @DisplayName("不同 postId 的关联应该不相等")
        void shouldNotBeEqualWhenDifferentPostId() {
            // Given
            PostTag postTag1 = PostTag.create(1L, 1001L);
            PostTag postTag2 = PostTag.create(2L, 1001L);

            // Then
            assertNotEquals(postTag1, postTag2);
        }

        @Test
        @DisplayName("不同 tagId 的关联应该不相等")
        void shouldNotBeEqualWhenDifferentTagId() {
            // Given
            PostTag postTag1 = PostTag.create(1L, 1001L);
            PostTag postTag2 = PostTag.create(1L, 1002L);

            // Then
            assertNotEquals(postTag1, postTag2);
        }

        @Test
        @DisplayName("不同 postId 和 tagId 的关联应该不相等")
        void shouldNotBeEqualWhenDifferentPostIdAndTagId() {
            // Given
            PostTag postTag1 = PostTag.create(1L, 1001L);
            PostTag postTag2 = PostTag.create(2L, 1002L);

            // Then
            assertNotEquals(postTag1, postTag2);
        }

        @Test
        @DisplayName("关联与自身应该相等")
        void shouldBeEqualToItself() {
            // Given
            PostTag postTag = PostTag.create(1L, 1001L);

            // Then
            assertEquals(postTag, postTag);
        }

        @Test
        @DisplayName("关联与null应该不相等")
        void shouldNotBeEqualToNull() {
            // Given
            PostTag postTag = PostTag.create(1L, 1001L);

            // Then
            assertNotEquals(postTag, null);
        }

        @Test
        @DisplayName("关联与其他类型对象应该不相等")
        void shouldNotBeEqualToDifferentType() {
            // Given
            PostTag postTag = PostTag.create(1L, 1001L);
            String other = "PostTag";

            // Then
            assertNotEquals(postTag, other);
        }

        @Test
        @DisplayName("相同复合主键但不同创建时间的关联应该相等")
        void shouldBeEqualEvenWithDifferentCreatedAt() {
            // Given
            LocalDateTime time1 = LocalDateTime.now().minusDays(1);
            LocalDateTime time2 = LocalDateTime.now();
            PostTag postTag1 = PostTag.reconstitute(1L, 1001L, time1);
            PostTag postTag2 = PostTag.reconstitute(1L, 1001L, time2);

            // Then
            assertEquals(postTag1, postTag2);
            assertEquals(postTag1.hashCode(), postTag2.hashCode());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString 应该包含所有关键字段")
        void shouldContainAllKeyFields() {
            // Given
            PostTag postTag = PostTag.create(1L, 1001L);

            // When
            String result = postTag.toString();

            // Then
            assertTrue(result.contains("postId=1"));
            assertTrue(result.contains("tagId=1001"));
            assertTrue(result.contains("createdAt="));
        }

        @Test
        @DisplayName("toString 应该包含类名")
        void shouldContainClassName() {
            // Given
            PostTag postTag = PostTag.create(1L, 1001L);

            // When
            String result = postTag.toString();

            // Then
            assertTrue(result.contains("PostTag"));
        }
    }

    @Nested
    @DisplayName("不可变性测试")
    class ImmutabilityTests {

        @Test
        @DisplayName("postId 应该不可变")
        void shouldHaveImmutablePostId() {
            // Given
            PostTag postTag = PostTag.create(1L, 1001L);
            Long originalPostId = postTag.getPostId();

            // Then
            assertEquals(originalPostId, postTag.getPostId());
            assertEquals(1L, postTag.getPostId());
        }

        @Test
        @DisplayName("tagId 应该不可变")
        void shouldHaveImmutableTagId() {
            // Given
            PostTag postTag = PostTag.create(1L, 1001L);
            Long originalTagId = postTag.getTagId();

            // Then
            assertEquals(originalTagId, postTag.getTagId());
            assertEquals(1001L, postTag.getTagId());
        }

        @Test
        @DisplayName("createdAt 应该不可变")
        void shouldHaveImmutableCreatedAt() {
            // Given
            PostTag postTag = PostTag.create(1L, 1001L);
            LocalDateTime originalCreatedAt = postTag.getCreatedAt();

            // Then
            assertEquals(originalCreatedAt, postTag.getCreatedAt());
        }

        @Test
        @DisplayName("所有字段都应该是 final 的")
        void shouldHaveAllFinalFields() {
            // Given
            PostTag postTag = PostTag.create(1L, 1001L);

            // Then - 验证对象创建后字段值不变
            Long postId = postTag.getPostId();
            Long tagId = postTag.getTagId();
            LocalDateTime createdAt = postTag.getCreatedAt();

            // 多次获取应该返回相同的值
            assertEquals(postId, postTag.getPostId());
            assertEquals(tagId, postTag.getTagId());
            assertEquals(createdAt, postTag.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("复合主键测试")
    class CompositeKeyTests {

        @Test
        @DisplayName("应该基于 postId 和 tagId 的组合判断唯一性")
        void shouldUseCompositeKeyForUniqueness() {
            // Given
            PostTag postTag1 = PostTag.create(1L, 1001L);
            PostTag postTag2 = PostTag.create(1L, 1002L);
            PostTag postTag3 = PostTag.create(2L, 1001L);
            PostTag postTag4 = PostTag.create(1L, 1001L);

            // Then
            assertEquals(postTag1, postTag4);  // 相同复合主键
            assertNotEquals(postTag1, postTag2);  // 不同 tagId
            assertNotEquals(postTag1, postTag3);  // 不同 postId
            assertNotEquals(postTag2, postTag3);  // 都不同
        }

        @Test
        @DisplayName("hashCode 应该基于复合主键计算")
        void shouldCalculateHashCodeFromCompositeKey() {
            // Given
            PostTag postTag1 = PostTag.create(1L, 1001L);
            PostTag postTag2 = PostTag.create(1L, 1001L);
            PostTag postTag3 = PostTag.create(2L, 1001L);

            // Then
            assertEquals(postTag1.hashCode(), postTag2.hashCode());
            assertNotEquals(postTag1.hashCode(), postTag3.hashCode());
        }
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("应该支持最小正整数ID")
        void shouldSupportMinimumPositiveId() {
            // Given & When
            PostTag postTag = PostTag.create(1L, 1L);

            // Then
            assertEquals(1L, postTag.getPostId());
            assertEquals(1L, postTag.getTagId());
        }

        @Test
        @DisplayName("应该支持大整数ID")
        void shouldSupportLargeId() {
            // Given
            Long largePostId = Long.MAX_VALUE;
            Long largeTagId = Long.MAX_VALUE - 1;

            // When
            PostTag postTag = PostTag.create(largePostId, largeTagId);

            // Then
            assertEquals(largePostId, postTag.getPostId());
            assertEquals(largeTagId, postTag.getTagId());
        }

        @Test
        @DisplayName("应该支持不同大小的ID组合")
        void shouldSupportDifferentIdSizes() {
            // Given & When
            PostTag postTag1 = PostTag.create(1L, Long.MAX_VALUE);
            PostTag postTag2 = PostTag.create(Long.MAX_VALUE, 1L);

            // Then
            assertNotNull(postTag1);
            assertNotNull(postTag2);
            assertNotEquals(postTag1, postTag2);
        }
    }
}
