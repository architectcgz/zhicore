package com.zhicore.content.application.query.model;

import com.zhicore.common.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 光标分页集成测试（R10）
 *
 * 覆盖：复合游标编解码、非法游标、首页请求
 * 注意：无重复/无遗漏的翻页测试需要在 Repository 集成测试中验证
 */
@DisplayName("CursorToken 光标分页测试")
class CursorPaginationTest {

    // ==================== 7.2 游标编码解码测试 ====================

    @Nested
    @DisplayName("游标编码解码")
    class EncodeDecodeTests {

        @Test
        @DisplayName("编码后解码能还原原始值")
        void encodeDecodeShouldRoundTrip() {
            LocalDateTime publishedAt = LocalDateTime.of(2026, 2, 24, 12, 30, 45);
            Long postId = 12345L;
            CursorToken original = new CursorToken(publishedAt, postId);

            String encoded = CursorToken.encode(original);
            CursorToken decoded = CursorToken.decodeOrThrow(encoded);

            assertThat(decoded.getPublishedAt()).isEqualTo(publishedAt);
            assertThat(decoded.getPostId()).isEqualTo(postId);
        }

        @Test
        @DisplayName("编码格式为 Base64Url")
        void encodedStringShouldBeBase64Url() {
            CursorToken token = new CursorToken(
                    LocalDateTime.of(2026, 1, 1, 0, 0), 1L
            );
            String encoded = CursorToken.encode(token);

            // Base64Url 不包含 +, /, = 字符（使用 - 和 _ 替代）
            assertThat(encoded).doesNotContain("+", "/");
            assertThat(encoded).isNotBlank();
        }

        @Test
        @DisplayName("null token 编码返回 null")
        void encodeNullShouldReturnNull() {
            assertThat(CursorToken.encode(null)).isNull();
        }

        @Test
        @DisplayName("publishedAt 为 null 的 token 编码返回 null")
        void encodeNullPublishedAtShouldReturnNull() {
            CursorToken token = new CursorToken(null, 1L);
            assertThat(CursorToken.encode(token)).isNull();
        }
    }

    // ==================== 7.3 非法游标测试 ====================

    @Nested
    @DisplayName("非法游标测试")
    class InvalidCursorTests {

        @Test
        @DisplayName("非法 Base64 字符串应抛出 ValidationException")
        void invalidBase64ShouldThrow() {
            assertThatThrownBy(() -> CursorToken.decodeOrThrow("not-valid-base64!!!"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("格式错误的游标内容应抛出 ValidationException")
        void malformedContentShouldThrow() {
            // 编码一个不包含 | 分隔符的字符串
            String encoded = java.util.Base64.getUrlEncoder()
                    .encodeToString("invalid-content".getBytes());
            assertThatThrownBy(() -> CursorToken.decodeOrThrow(encoded))
                    .isInstanceOf(ValidationException.class);
        }
    }

    // ==================== 7.4 首页请求测试 ====================

    @Nested
    @DisplayName("首页请求测试")
    class FirstPageTests {

        @Test
        @DisplayName("cursor 为 null 时 decodeOrThrow 返回 null（视为首页）")
        void nullCursorShouldReturnNull() {
            assertThat(CursorToken.decodeOrThrow(null)).isNull();
        }

        @Test
        @DisplayName("cursor 为空字符串时 decodeOrThrow 返回 null（视为首页）")
        void emptyCursorShouldReturnNull() {
            assertThat(CursorToken.decodeOrThrow("")).isNull();
            assertThat(CursorToken.decodeOrThrow("   ")).isNull();
        }
    }
}
