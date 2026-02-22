package com.zhicore.search.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostDocument 单元测试
 *
 * @author ZhiCore Team
 */
@DisplayName("PostDocument 测试")
class PostDocumentTest {

    @Nested
    @DisplayName("构建器测试")
    class BuilderTests {

        @Test
        @DisplayName("使用构建器创建完整文档")
        void builder_CreateFullDocument() {
            // Given
            String id = "1";
            String title = "测试文章标题";
            String content = "这是文章内容";
            String excerpt = "这是摘要";
            String authorId = "user-001";
            String authorName = "测试作者";
            List<PostDocument.TagInfo> tags = Arrays.asList(
                PostDocument.TagInfo.builder().id("1").name("Java").slug("java").build(),
                PostDocument.TagInfo.builder().id("2").name("Spring").slug("spring").build()
            );
            String categoryId = "10";
            String categoryName = "技术";
            String status = "PUBLISHED";
            Integer likeCount = 100;
            Integer commentCount = 50;
            Long viewCount = 1000L;
            LocalDateTime publishedAt = LocalDateTime.now();
            LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
            LocalDateTime updatedAt = LocalDateTime.now();

            // When
            PostDocument document = PostDocument.builder()
                .id(id)
                .title(title)
                .content(content)
                .excerpt(excerpt)
                .authorId(authorId)
                .authorName(authorName)
                .tags(tags)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .status(status)
                .likeCount(likeCount)
                .commentCount(commentCount)
                .viewCount(viewCount)
                .publishedAt(publishedAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

            // Then
            assertEquals(id, document.getId());
            assertEquals(title, document.getTitle());
            assertEquals(content, document.getContent());
            assertEquals(excerpt, document.getExcerpt());
            assertEquals(authorId, document.getAuthorId());
            assertEquals(authorName, document.getAuthorName());
            assertEquals(tags, document.getTags());
            assertEquals(categoryId, document.getCategoryId());
            assertEquals(categoryName, document.getCategoryName());
            assertEquals(status, document.getStatus());
            assertEquals(likeCount, document.getLikeCount());
            assertEquals(commentCount, document.getCommentCount());
            assertEquals(viewCount, document.getViewCount());
            assertEquals(publishedAt, document.getPublishedAt());
            assertEquals(createdAt, document.getCreatedAt());
            assertEquals(updatedAt, document.getUpdatedAt());
        }

        @Test
        @DisplayName("使用构建器创建最小文档")
        void builder_CreateMinimalDocument() {
            // When
            PostDocument document = PostDocument.builder()
                .id("1")
                .title("标题")
                .build();

            // Then
            assertEquals("1", document.getId());
            assertEquals("标题", document.getTitle());
            assertNull(document.getContent());
            assertNull(document.getExcerpt());
            assertNull(document.getAuthorId());
            assertNull(document.getTags());
        }
    }

    @Nested
    @DisplayName("无参构造器测试")
    class NoArgsConstructorTests {

        @Test
        @DisplayName("使用无参构造器创建文档")
        void noArgsConstructor_CreateDocument() {
            // When
            PostDocument document = new PostDocument();

            // Then
            assertNull(document.getId());
            assertNull(document.getTitle());
        }

        @Test
        @DisplayName("使用 setter 设置属性")
        void setter_SetProperties() {
            // Given
            PostDocument document = new PostDocument();

            // When
            document.setId("1");
            document.setTitle("测试标题");
            document.setTags(Arrays.asList(
                PostDocument.TagInfo.builder().id("1").name("tag1").slug("tag1").build(),
                PostDocument.TagInfo.builder().id("2").name("tag2").slug("tag2").build()
            ));

            // Then
            assertEquals("1", document.getId());
            assertEquals("测试标题", document.getTitle());
            assertEquals(2, document.getTags().size());
        }
    }

    @Nested
    @DisplayName("全参构造器测试")
    class AllArgsConstructorTests {

        @Test
        @DisplayName("使用全参构造器创建文档")
        void allArgsConstructor_CreateDocument() {
            // Given
            LocalDateTime now = LocalDateTime.now();
            List<PostDocument.TagInfo> tags = Arrays.asList(
                PostDocument.TagInfo.builder().id("1").name("Java").slug("java").build(),
                PostDocument.TagInfo.builder().id("2").name("Spring").slug("spring").build()
            );

            // When
            PostDocument document = new PostDocument(
                "1", "标题", "内容", "摘要",
                "author-001", "作者名",
                tags, "10", "技术",
                "PUBLISHED", 100, 50, 1000L,
                now, now.minusDays(1), now
            );

            // Then
            assertEquals("1", document.getId());
            assertEquals("标题", document.getTitle());
            assertEquals("内容", document.getContent());
            assertEquals("摘要", document.getExcerpt());
            assertEquals("author-001", document.getAuthorId());
            assertEquals("作者名", document.getAuthorName());
            assertEquals(tags, document.getTags());
            assertEquals("10", document.getCategoryId());
            assertEquals("技术", document.getCategoryName());
            assertEquals("PUBLISHED", document.getStatus());
            assertEquals(100, document.getLikeCount());
            assertEquals(50, document.getCommentCount());
            assertEquals(1000L, document.getViewCount());
        }
    }

    @Nested
    @DisplayName("equals 和 hashCode 测试")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("相同属性的文档应该相等")
        void equals_SameProperties() {
            // Given
            PostDocument doc1 = PostDocument.builder()
                .id("1")
                .title("标题")
                .content("内容")
                .build();

            PostDocument doc2 = PostDocument.builder()
                .id("1")
                .title("标题")
                .content("内容")
                .build();

            // Then
            assertEquals(doc1, doc2);
            assertEquals(doc1.hashCode(), doc2.hashCode());
        }

        @Test
        @DisplayName("不同 ID 的文档不相等")
        void equals_DifferentId() {
            // Given
            PostDocument doc1 = PostDocument.builder()
                .id("1")
                .title("标题")
                .build();

            PostDocument doc2 = PostDocument.builder()
                .id("2")
                .title("标题")
                .build();

            // Then
            assertNotEquals(doc1, doc2);
        }
    }
}
