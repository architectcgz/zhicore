package com.zhicore.content.application.query;

import com.zhicore.content.application.query.view.PostDetailView;
import com.zhicore.content.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 发布时间映射测试（R9）
 *
 * 覆盖：publishedAt 使用真实发布时间、未发布文章 publishedAt 为 null
 */
@DisplayName("PostDetailView 发布时间映射测试")
class PostDetailViewMappingTest {

    @Nested
    @DisplayName("publishedAt 映射")
    class PublishedAtMappingTests {

        @Test
        @DisplayName("已发布文章 publishedAt 为实际发布时间")
        void publishedPostShouldHaveActualPublishedAt() {
            LocalDateTime publishedTime = LocalDateTime.of(2026, 2, 20, 10, 30);

            PostDetailView view = PostDetailView.builder()
                    .id(PostId.of(1L))
                    .title("测试文章")
                    .status(PostStatus.PUBLISHED)
                    .publishedAt(publishedTime)
                    .createdAt(LocalDateTime.of(2026, 2, 18, 8, 0))
                    .build();

            assertThat(view.getPublishedAt()).isEqualTo(publishedTime);
            // publishedAt 不应等于 createdAt
            assertThat(view.getPublishedAt()).isNotEqualTo(view.getCreatedAt());
        }

        @Test
        @DisplayName("未发布文章 publishedAt 为 null")
        void draftPostShouldHaveNullPublishedAt() {
            PostDetailView view = PostDetailView.builder()
                    .id(PostId.of(2L))
                    .title("草稿文章")
                    .status(PostStatus.DRAFT)
                    .createdAt(LocalDateTime.now())
                    .build();

            assertThat(view.getPublishedAt()).isNull();
        }
    }
}
