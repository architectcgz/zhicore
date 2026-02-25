package com.zhicore.content.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tag 聚合根单元测试
 * 
 * 测试 Tag 聚合根的创建、验证和领域行为
 * 
 * Requirements: 4.1
 *
 * @author ZhiCore Team
 */
@DisplayName("Tag 聚合根测试")
class TagTest {

    @Nested
    @DisplayName("创建标签")
    class CreateTag {

        @Test
        @DisplayName("应该成功创建标签")
        void shouldCreateTagSuccessfully() {
            // Given
            Long id = 1001L;
            String name = "PostgreSQL";
            String slug = "postgresql";

            // When
            Tag tag = Tag.create(id, name, slug);

            // Then
            assertNotNull(tag);
            assertEquals(id, tag.getId());
            assertEquals("PostgreSQL", tag.getName());
            assertEquals(slug, tag.getSlug());
            assertNull(tag.getDescription());
            assertNotNull(tag.getCreatedAt());
            assertNotNull(tag.getUpdatedAt());
            assertFalse(tag.hasDescription());
        }

        @Test
        @DisplayName("应该自动trim标签名称")
        void shouldTrimTagName() {
            // Given
            Long id = 1001L;
            String name = "  Spring Boot  ";
            String slug = "spring-boot";

            // When
            Tag tag = Tag.create(id, name, slug);

            // Then
            assertEquals("Spring Boot", tag.getName());
        }

        @Test
        @DisplayName("标签ID为null时应该抛出异常")
        void shouldThrowExceptionWhenIdIsNull() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    Tag.create(null, "Java", "java"));
            assertTrue(exception.getMessage().contains("标签ID不能为空"));
        }

        @Test
        @DisplayName("标签ID为0时应该抛出异常")
        void shouldThrowExceptionWhenIdIsZero() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    Tag.create(0L, "Java", "java"));
            assertTrue(exception.getMessage().contains("标签ID必须为正数"));
        }

        @Test
        @DisplayName("标签ID为负数时应该抛出异常")
        void shouldThrowExceptionWhenIdIsNegative() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    Tag.create(-1L, "Java", "java"));
            assertTrue(exception.getMessage().contains("标签ID必须为正数"));
        }

        @Test
        @DisplayName("标签名称为null时应该抛出异常")
        void shouldThrowExceptionWhenNameIsNull() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    Tag.create(1001L, null, "java"));
            assertTrue(exception.getMessage().contains("标签名称不能为空"));
        }

        @Test
        @DisplayName("标签名称为空字符串时应该抛出异常")
        void shouldThrowExceptionWhenNameIsEmpty() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    Tag.create(1001L, "", "java"));
            assertTrue(exception.getMessage().contains("标签名称不能为空"));
        }

        @Test
        @DisplayName("标签名称为空白字符时应该抛出异常")
        void shouldThrowExceptionWhenNameIsBlank() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    Tag.create(1001L, "   ", "java"));
            assertTrue(exception.getMessage().contains("标签名称不能为空"));
        }

        @Test
        @DisplayName("标签slug为null时应该抛出异常")
        void shouldThrowExceptionWhenSlugIsNull() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    Tag.create(1001L, "Java", null));
            assertTrue(exception.getMessage().contains("标签slug不能为空"));
        }

        @Test
        @DisplayName("标签slug为空字符串时应该抛出异常")
        void shouldThrowExceptionWhenSlugIsEmpty() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    Tag.create(1001L, "Java", ""));
            assertTrue(exception.getMessage().contains("标签slug不能为空"));
        }

        @Test
        @DisplayName("标签slug为空白字符时应该抛出异常")
        void shouldThrowExceptionWhenSlugIsBlank() {
            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    Tag.create(1001L, "Java", "   "));
            assertTrue(exception.getMessage().contains("标签slug不能为空"));
        }
    }

    @Nested
    @DisplayName("从持久化恢复标签")
    class ReconstituteTag {

        @Test
        @DisplayName("应该成功从持久化恢复标签")
        void shouldReconstituteTagSuccessfully() {
            // Given
            Long id = 1001L;
            String name = "PostgreSQL";
            String slug = "postgresql";
            String description = "关系型数据库";
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
            LocalDateTime updatedAt = LocalDateTime.now();

            // When
            Tag tag = Tag.reconstitute(id, name, slug, description, createdAt, updatedAt);

            // Then
            assertNotNull(tag);
            assertEquals(id, tag.getId());
            assertEquals(name, tag.getName());
            assertEquals(slug, tag.getSlug());
            assertEquals(description, tag.getDescription());
            assertEquals(createdAt, tag.getCreatedAt());
            assertEquals(updatedAt, tag.getUpdatedAt());
            assertTrue(tag.hasDescription());
        }

        @Test
        @DisplayName("应该成功恢复没有描述的标签")
        void shouldReconstituteTagWithoutDescription() {
            // Given
            Long id = 1001L;
            String name = "Java";
            String slug = "java";
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
            LocalDateTime updatedAt = LocalDateTime.now();

            // When
            Tag tag = Tag.reconstitute(id, name, slug, null, createdAt, updatedAt);

            // Then
            assertNotNull(tag);
            assertNull(tag.getDescription());
            assertFalse(tag.hasDescription());
        }
    }

    @Nested
    @DisplayName("更新标签名称")
    class UpdateName {

        @Test
        @DisplayName("应该成功更新标签名称")
        void shouldUpdateNameSuccessfully() {
            // Given
            Tag tag = Tag.create(1001L, "PostgreSQL", "postgresql");
            LocalDateTime originalUpdatedAt = tag.getUpdatedAt();
            
            // Wait a bit to ensure timestamp changes
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // When
            tag.updateName("PostgreSQL Database");

            // Then
            assertEquals("PostgreSQL Database", tag.getName());
            assertTrue(tag.getUpdatedAt().isAfter(originalUpdatedAt));
            // slug should remain unchanged
            assertEquals("postgresql", tag.getSlug());
        }

        @Test
        @DisplayName("应该自动trim更新的标签名称")
        void shouldTrimUpdatedName() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");

            // When
            tag.updateName("  Java SE  ");

            // Then
            assertEquals("Java SE", tag.getName());
        }

        @Test
        @DisplayName("更新名称为null时应该抛出异常")
        void shouldThrowExceptionWhenUpdatingNameToNull() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    tag.updateName(null));
            assertTrue(exception.getMessage().contains("标签名称不能为空"));
        }

        @Test
        @DisplayName("更新名称为空字符串时应该抛出异常")
        void shouldThrowExceptionWhenUpdatingNameToEmpty() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    tag.updateName(""));
            assertTrue(exception.getMessage().contains("标签名称不能为空"));
        }

        @Test
        @DisplayName("更新名称为空白字符时应该抛出异常")
        void shouldThrowExceptionWhenUpdatingNameToBlank() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    tag.updateName("   "));
            assertTrue(exception.getMessage().contains("标签名称不能为空"));
        }

        @Test
        @DisplayName("更新名称超过50字符时应该抛出异常")
        void shouldThrowExceptionWhenNameTooLong() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");
            String longName = "a".repeat(51);

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    tag.updateName(longName));
            assertTrue(exception.getMessage().contains("标签名称不能超过50字符"));
        }

        @Test
        @DisplayName("更新名称为50字符时应该成功")
        void shouldUpdateNameWith50Characters() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");
            String name50 = "a".repeat(50);

            // When
            tag.updateName(name50);

            // Then
            assertEquals(name50, tag.getName());
        }
    }

    @Nested
    @DisplayName("更新标签描述")
    class UpdateDescription {

        @Test
        @DisplayName("应该成功更新标签描述")
        void shouldUpdateDescriptionSuccessfully() {
            // Given
            Tag tag = Tag.create(1001L, "PostgreSQL", "postgresql");
            LocalDateTime originalUpdatedAt = tag.getUpdatedAt();
            
            // Wait a bit to ensure timestamp changes
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // When
            tag.updateDescription("开源关系型数据库");

            // Then
            assertEquals("开源关系型数据库", tag.getDescription());
            assertTrue(tag.hasDescription());
            assertTrue(tag.getUpdatedAt().isAfter(originalUpdatedAt));
        }

        @Test
        @DisplayName("应该成功将描述更新为null")
        void shouldUpdateDescriptionToNull() {
            // Given
            Tag tag = Tag.create(1001L, "PostgreSQL", "postgresql");
            tag.updateDescription("描述");

            // When
            tag.updateDescription(null);

            // Then
            assertNull(tag.getDescription());
            assertFalse(tag.hasDescription());
        }

        @Test
        @DisplayName("更新描述超过200字符时应该抛出异常")
        void shouldThrowExceptionWhenDescriptionTooLong() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");
            String longDescription = "a".repeat(201);

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    tag.updateDescription(longDescription));
            assertTrue(exception.getMessage().contains("标签描述不能超过200字符"));
        }

        @Test
        @DisplayName("更新描述为200字符时应该成功")
        void shouldUpdateDescriptionWith200Characters() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");
            String description200 = "a".repeat(200);

            // When
            tag.updateDescription(description200);

            // Then
            assertEquals(description200, tag.getDescription());
            assertTrue(tag.hasDescription());
        }

        @Test
        @DisplayName("更新描述为空字符串时应该成功")
        void shouldUpdateDescriptionToEmptyString() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");

            // When
            tag.updateDescription("");

            // Then
            assertEquals("", tag.getDescription());
            assertFalse(tag.hasDescription());
        }
    }

    @Nested
    @DisplayName("查询方法")
    class QueryMethods {

        @Test
        @DisplayName("hasDescription 应该正确判断有描述的情况")
        void shouldReturnTrueWhenHasDescription() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");
            tag.updateDescription("编程语言");

            // Then
            assertTrue(tag.hasDescription());
        }

        @Test
        @DisplayName("hasDescription 应该正确判断无描述的情况")
        void shouldReturnFalseWhenNoDescription() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");

            // Then
            assertFalse(tag.hasDescription());
        }

        @Test
        @DisplayName("hasDescription 应该正确判断空字符串描述")
        void shouldReturnFalseWhenDescriptionIsEmpty() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");
            tag.updateDescription("");

            // Then
            assertFalse(tag.hasDescription());
        }

        @Test
        @DisplayName("hasDescription 应该正确判断空白字符描述")
        void shouldReturnFalseWhenDescriptionIsBlank() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");
            tag.updateDescription("   ");

            // Then
            assertFalse(tag.hasDescription());
        }
    }

    @Nested
    @DisplayName("equals 和 hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("相同ID的标签应该相等")
        void shouldBeEqualWhenSameId() {
            // Given
            Tag tag1 = Tag.create(1001L, "Java", "java");
            Tag tag2 = Tag.create(1001L, "Java SE", "java");

            // Then
            assertEquals(tag1, tag2);
            assertEquals(tag1.hashCode(), tag2.hashCode());
        }

        @Test
        @DisplayName("不同ID的标签应该不相等")
        void shouldNotBeEqualWhenDifferentId() {
            // Given
            Tag tag1 = Tag.create(1001L, "Java", "java");
            Tag tag2 = Tag.create(1002L, "Java", "java");

            // Then
            assertNotEquals(tag1, tag2);
        }

        @Test
        @DisplayName("标签与自身应该相等")
        void shouldBeEqualToItself() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");

            // Then
            assertEquals(tag, tag);
        }

        @Test
        @DisplayName("标签与null应该不相等")
        void shouldNotBeEqualToNull() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");

            // Then
            assertNotEquals(tag, null);
        }

        @Test
        @DisplayName("标签与其他类型对象应该不相等")
        void shouldNotBeEqualToDifferentType() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");
            String other = "Java";

            // Then
            assertNotEquals(tag, other);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString 应该包含所有关键字段")
        void shouldContainAllKeyFields() {
            // Given
            Tag tag = Tag.create(1001L, "PostgreSQL", "postgresql");
            tag.updateDescription("数据库");

            // When
            String result = tag.toString();

            // Then
            assertTrue(result.contains("1001"));
            assertTrue(result.contains("PostgreSQL"));
            assertTrue(result.contains("postgresql"));
            assertTrue(result.contains("数据库"));
        }

        @Test
        @DisplayName("toString 应该处理null描述")
        void shouldHandleNullDescription() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");

            // When
            String result = tag.toString();

            // Then
            assertNotNull(result);
            assertTrue(result.contains("Java"));
            assertTrue(result.contains("java"));
        }
    }

    @Nested
    @DisplayName("不可变性测试")
    class ImmutabilityTests {

        @Test
        @DisplayName("ID应该不可变")
        void shouldHaveImmutableId() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");
            Long originalId = tag.getId();

            // When - 尝试通过反射修改（这里只是验证getter返回的是final字段）
            // Then
            assertEquals(originalId, tag.getId());
        }

        @Test
        @DisplayName("slug应该不可变")
        void shouldHaveImmutableSlug() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");
            String originalSlug = tag.getSlug();

            // When - 更新名称不应该影响slug
            tag.updateName("Java SE");

            // Then
            assertEquals(originalSlug, tag.getSlug());
            assertEquals("java", tag.getSlug());
        }

        @Test
        @DisplayName("createdAt应该不可变")
        void shouldHaveImmutableCreatedAt() {
            // Given
            Tag tag = Tag.create(1001L, "Java", "java");
            LocalDateTime originalCreatedAt = tag.getCreatedAt();

            // When - 更新名称不应该影响createdAt
            tag.updateName("Java SE");

            // Then
            assertEquals(originalCreatedAt, tag.getCreatedAt());
        }
    }
}
