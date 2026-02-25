package com.zhicore.content.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文章状态安全解析测试（R8）
 *
 * 覆盖：非法状态解析、合法状态解析
 */
@DisplayName("PostStatus 解析测试")
class PostStatusParsingTest {

    @Nested
    @DisplayName("合法状态解析")
    class ValidStatusTests {

        @Test
        @DisplayName("fromCode(0) 返回 DRAFT")
        void code0ShouldReturnDraft() {
            assertThat(PostStatus.fromCode(0)).isEqualTo(PostStatus.DRAFT);
        }

        @Test
        @DisplayName("fromCode(1) 返回 PUBLISHED")
        void code1ShouldReturnPublished() {
            assertThat(PostStatus.fromCode(1)).isEqualTo(PostStatus.PUBLISHED);
        }

        @Test
        @DisplayName("fromCode(2) 返回 SCHEDULED")
        void code2ShouldReturnScheduled() {
            assertThat(PostStatus.fromCode(2)).isEqualTo(PostStatus.SCHEDULED);
        }

        @Test
        @DisplayName("fromCode(3) 返回 DELETED")
        void code3ShouldReturnDeleted() {
            assertThat(PostStatus.fromCode(3)).isEqualTo(PostStatus.DELETED);
        }

        @Test
        @DisplayName("所有枚举值的 code 和 description 非空")
        void allValuesShouldHaveCodeAndDescription() {
            for (PostStatus status : PostStatus.values()) {
                assertThat(status.getDescription()).isNotBlank();
                // code 应在 0-3 范围内
                assertThat(status.getCode()).isBetween(0, 3);
            }
        }
    }

    @Nested
    @DisplayName("非法状态解析")
    class InvalidStatusTests {

        @ParameterizedTest(name = "非法 code={0} 应抛出 IllegalArgumentException")
        @ValueSource(ints = {-1, 4, 99, Integer.MAX_VALUE, Integer.MIN_VALUE})
        @DisplayName("非法 code 应抛出 IllegalArgumentException")
        void invalidCodeShouldThrow(int invalidCode) {
            assertThatThrownBy(() -> PostStatus.fromCode(invalidCode))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown PostStatus code");
        }
    }
}
