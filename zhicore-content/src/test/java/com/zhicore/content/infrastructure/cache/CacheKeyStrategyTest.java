package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存键策略测试（R12）
 *
 * 覆盖：缓存键包含 size、status 占位符、缓存键版本化
 */
@DisplayName("PostRedisKeys 缓存键策略测试")
class CacheKeyStrategyTest {

    // ==================== 9.1 缓存键包含 size 测试 ====================

    @Nested
    @DisplayName("缓存键包含 size")
    class SizeInKeyTests {

        @Test
        @DisplayName("不同 size 参数使用不同缓存键")
        void differentSizeShouldProduceDifferentKeys() {
            String key10 = PostRedisKeys.listLatest(1, 10);
            String key20 = PostRedisKeys.listLatest(1, 20);

            assertThat(key10).isNotEqualTo(key20);
            assertThat(key10).contains(":size:10:");
            assertThat(key20).contains(":size:20:");
        }

        @Test
        @DisplayName("游标分页 key 也包含 size")
        void cursorKeyShouldContainSize() {
            String key = PostRedisKeys.listLatestCursor("abc", 15);
            assertThat(key).contains(":size:15:");
        }
    }

    // ==================== 9.2 status 占位符测试 ====================

    @Nested
    @DisplayName("status 占位符")
    class StatusPlaceholderTests {

        @Test
        @DisplayName("列表 key 包含 status 维度")
        void listKeyShouldContainStatus() {
            String key = PostRedisKeys.listLatest(1, 20);
            assertThat(key).contains(":status:");
        }

        @Test
        @DisplayName("作者列表 key 使用 ALL 占位")
        void authorListKeyShouldUseAllPlaceholder() {
            String key = PostRedisKeys.listAuthor(UserId.of(1L), 1, 20);
            assertThat(key).contains(":status:ALL:");
        }

        @Test
        @DisplayName("不同维度使用不同 key")
        void differentDimensionsShouldProduceDifferentKeys() {
            String latestKey = PostRedisKeys.listLatest(1, 20);
            String authorKey = PostRedisKeys.listAuthor(UserId.of(1L), 1, 20);
            String tagKey = PostRedisKeys.listTag(TagId.of(1L), 1, 20);

            assertThat(latestKey).isNotEqualTo(authorKey);
            assertThat(latestKey).isNotEqualTo(tagKey);
            assertThat(authorKey).isNotEqualTo(tagKey);
        }
    }

    // ==================== 9.3 缓存键版本化测试 ====================

    @Nested
    @DisplayName("缓存键版本化")
    class VersionTests {

        @Test
        @DisplayName("列表 key 包含版本前缀")
        void listKeyShouldContainVersion() {
            String key = PostRedisKeys.listLatest(1, 20);
            // 当前版本为 v2
            assertThat(key).contains(":v2:");
        }

        @Test
        @DisplayName("游标分页 key 包含版本前缀")
        void cursorKeyShouldContainVersion() {
            String key = PostRedisKeys.listLatestCursor("cursor", 20);
            assertThat(key).contains(":v2:");
        }

        @Test
        @DisplayName("作者列表 key 包含版本前缀")
        void authorListKeyShouldContainVersion() {
            String key = PostRedisKeys.listAuthor(UserId.of(1L), 1, 20);
            assertThat(key).contains(":v2:");
        }

        @Test
        @DisplayName("标签列表 key 包含版本前缀")
        void tagListKeyShouldContainVersion() {
            String key = PostRedisKeys.listTag(TagId.of(1L), 1, 20);
            assertThat(key).contains(":v2:");
        }
    }

    // ==================== 补充：游标为空时使用 INIT 占位 ====================

    @Nested
    @DisplayName("游标占位符")
    class CursorPlaceholderTests {

        @Test
        @DisplayName("cursor 为 null 时使用 INIT 占位")
        void nullCursorShouldUseInit() {
            String key = PostRedisKeys.listLatestCursor(null, 20);
            assertThat(key).contains(":cursor:INIT");
        }

        @Test
        @DisplayName("cursor 为空字符串时使用 INIT 占位")
        void emptyCursorShouldUseInit() {
            String key = PostRedisKeys.listLatestCursor("", 20);
            assertThat(key).contains(":cursor:INIT");
        }

        @Test
        @DisplayName("cursor 有值时使用实际值")
        void nonEmptyCursorShouldUseActualValue() {
            String key = PostRedisKeys.listLatestCursor("abc123", 20);
            assertThat(key).contains(":cursor:abc123");
        }
    }
}
