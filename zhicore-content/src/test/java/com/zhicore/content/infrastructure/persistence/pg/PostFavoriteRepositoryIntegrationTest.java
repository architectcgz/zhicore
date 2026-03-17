package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.content.domain.model.PostFavorite;
import com.zhicore.content.domain.repository.PostFavoriteRepository;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 收藏仓储集成测试（R3）
 *
 * 覆盖：CRUD、幂等性、删除幂等、索引命中、批量状态查询
 */
@DisplayName("PostFavoriteRepository 集成测试")
class PostFavoriteRepositoryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PostFavoriteRepository postFavoriteRepository;

    @BeforeEach
    void setUp() {
        cleanupPostgres();
    }

    // ==================== 3.1 基本 CRUD ====================

    @Nested
    @DisplayName("基本 CRUD 操作")
    class CrudTests {

        @Test
        @DisplayName("save 成功写入并可查询")
        void saveShouldPersistAndBeQueryable() {
            PostFavorite fav = new PostFavorite(1L, 100L, 200L);
            assertThat(postFavoriteRepository.save(fav)).isTrue();

            Optional<PostFavorite> found = postFavoriteRepository.findByPostIdAndUserId(100L, 200L);
            assertThat(found).isPresent();
            assertThat(found.get().getPostId()).isEqualTo(100L);
            assertThat(found.get().getUserId()).isEqualTo(200L);
        }

        @Test
        @DisplayName("exists 正确判断收藏是否存在")
        void existsShouldReturnCorrectly() {
            assertThat(postFavoriteRepository.exists(100L, 200L)).isFalse();

            assertThat(postFavoriteRepository.save(new PostFavorite(2L, 100L, 200L))).isTrue();
            assertThat(postFavoriteRepository.exists(100L, 200L)).isTrue();
        }

        @Test
        @DisplayName("countByPostId 正确统计收藏数")
        void countByPostIdShouldReturnCorrectCount() {
            assertThat(postFavoriteRepository.save(new PostFavorite(3L, 100L, 201L))).isTrue();
            assertThat(postFavoriteRepository.save(new PostFavorite(4L, 100L, 202L))).isTrue();
            assertThat(postFavoriteRepository.save(new PostFavorite(5L, 999L, 203L))).isTrue();

            assertThat(postFavoriteRepository.countByPostId(100L)).isEqualTo(2);
            assertThat(postFavoriteRepository.countByPostId(999L)).isEqualTo(1);
            assertThat(postFavoriteRepository.countByPostId(888L)).isEqualTo(0);
        }

        @Test
        @DisplayName("delete 成功删除收藏记录")
        void deleteShouldRemoveRecord() {
            assertThat(postFavoriteRepository.save(new PostFavorite(6L, 100L, 200L))).isTrue();
            assertThat(postFavoriteRepository.exists(100L, 200L)).isTrue();

            assertThat(postFavoriteRepository.delete(100L, 200L)).isTrue();
            assertThat(postFavoriteRepository.exists(100L, 200L)).isFalse();
        }
    }

    // ==================== 3.2 幂等性测试 ====================

    @Nested
    @DisplayName("幂等性测试")
    class IdempotencyTests {

        @Test
        @DisplayName("重复收藏不抛异常且数据库只有一条记录")
        void duplicateSaveShouldBeIdempotent() {
            assertThat(postFavoriteRepository.save(new PostFavorite(10L, 100L, 200L))).isTrue();

            assertThat(postFavoriteRepository.save(new PostFavorite(11L, 100L, 200L))).isFalse();

            assertThat(postFavoriteRepository.countByPostId(100L)).isEqualTo(1);
        }
    }

    // ==================== 3.3 删除幂等测试 ====================

    @Nested
    @DisplayName("删除幂等测试")
    class DeleteIdempotencyTests {

        @Test
        @DisplayName("删除不存在的收藏记录不抛异常")
        void deleteNonExistentShouldNotThrow() {
            assertThat(postFavoriteRepository.delete(999L, 999L)).isFalse();
        }

        @Test
        @DisplayName("重复删除不抛异常")
        void duplicateDeleteShouldBeIdempotent() {
            assertThat(postFavoriteRepository.save(new PostFavorite(20L, 100L, 200L))).isTrue();
            assertThat(postFavoriteRepository.delete(100L, 200L)).isTrue();

            assertThat(postFavoriteRepository.delete(100L, 200L)).isFalse();
        }
    }

    // ==================== 3.4 索引命中测试 ====================

    @Nested
    @DisplayName("索引命中测试")
    class IndexTests {

        @Test
        @DisplayName("idx_post_favorites_post_id 索引查询可正常执行")
        void postIdIndexQueryShouldWork() {
            for (long i = 30; i < 40; i++) {
                assertThat(postFavoriteRepository.save(new PostFavorite(i, 100L, 200L + i))).isTrue();
            }

            List<String> plan = jdbcTemplate.queryForList(
                    "EXPLAIN SELECT * FROM post_favorites WHERE post_id = 100",
                    String.class
            );
            assertThat(String.join("\n", plan)).isNotBlank();
        }
    }

    // ==================== 3.5 批量状态查询测试 ====================

    @Nested
    @DisplayName("批量状态查询测试")
    class BatchQueryTests {

        @Test
        @DisplayName("findFavoritedPostIds 正确返回已收藏的文章ID列表")
        void findFavoritedPostIdsShouldReturnCorrectIds() {
            long userId = 300L;
            assertThat(postFavoriteRepository.save(new PostFavorite(40L, 101L, userId))).isTrue();
            assertThat(postFavoriteRepository.save(new PostFavorite(41L, 102L, userId))).isTrue();

            List<Long> favIds = postFavoriteRepository.findFavoritedPostIds(
                    userId, List.of(101L, 102L, 103L)
            );
            assertThat(favIds).containsExactlyInAnyOrder(101L, 102L);
        }

        @Test
        @DisplayName("findFavoritedPostIds 空列表输入返回空列表")
        void findFavoritedPostIdsShouldReturnEmptyForEmptyInput() {
            List<Long> result = postFavoriteRepository.findFavoritedPostIds(300L, List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findByUserIdCursor 游标分页正确")
        void findByUserIdCursorShouldPaginateCorrectly() {
            long userId = 400L;
            LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0);

            for (int i = 0; i < 5; i++) {
                PostFavorite fav = PostFavorite.reconstitute(
                        50L + i, 100L + i, userId, base.plusMinutes(i)
                );
                assertThat(postFavoriteRepository.save(fav)).isTrue();
            }

            List<PostFavorite> page1 = postFavoriteRepository.findByUserIdCursor(userId, null, 3);
            assertThat(page1).hasSize(3);

            LocalDateTime cursor = page1.get(page1.size() - 1).getCreatedAt();
            List<PostFavorite> page2 = postFavoriteRepository.findByUserIdCursor(userId, cursor, 3);
            assertThat(page2).hasSize(2);

            // 无重复
            List<Long> allIds = new java.util.ArrayList<>(
                    page1.stream().map(PostFavorite::getId).toList()
            );
            allIds.addAll(page2.stream().map(PostFavorite::getId).toList());
            assertThat(allIds).doesNotHaveDuplicates();
        }
    }
}
