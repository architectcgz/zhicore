package com.zhicore.content.application.service.command;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.service.query.PostFavoriteQueryService;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("PostFavoriteCommandService 并发集成测试")
class PostFavoriteCommandServiceConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final long POST_ID = 93001L;
    private static final long USER_ID = 94001L;
    private static final long AUTHOR_ID = 95001L;

    @Autowired
    private PostFavoriteCommandService postFavoriteCommandService;

    @Autowired
    private PostFavoriteQueryService postFavoriteQueryService;

    @MockBean
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @BeforeEach
    void setUp() {
        cleanupRedis();
        cleanupPostgres();
        insertPublishedPost();
    }

    @Test
    @DisplayName("多个用户并发收藏时 post_stats.favorite_count 与关系表保持一致")
    void concurrentMultiUserFavoriteShouldKeepFavoriteStatsConsistent() throws Exception {
        int concurrency = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicLong idSequence = new AtomicLong(1_700_000L);

        when(idGeneratorFeignClient.generateSnowflakeId()).thenAnswer(invocation ->
                ApiResponse.success(idSequence.incrementAndGet()));

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                long userId = USER_ID + i;
                futures.add(executor.submit(() -> {
                    startLatch.await(5, TimeUnit.SECONDS);
                    postFavoriteCommandService.favoritePost(userId, POST_ID);
                    return null;
                }));
            }

            startLatch.countDown();

            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            long relationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post_favorites WHERE post_id = ?",
                    Long.class,
                    POST_ID
            );
            long outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?",
                    Long.class,
                    POST_ID,
                    "com.zhicore.integration.messaging.post.PostFavoritedIntegrationEvent"
            );
            Integer favoriteCount = jdbcTemplate.queryForObject(
                    "SELECT favorite_count FROM post_stats WHERE post_id = ?",
                    Integer.class,
                    POST_ID
            );
            Integer shareCount = jdbcTemplate.queryForObject(
                    "SELECT share_count FROM post_stats WHERE post_id = ?",
                    Integer.class,
                    POST_ID
            );
            int visibleFavoriteCount = postFavoriteQueryService.getFavoriteCount(POST_ID);
            Object cachedFavoriteCount = redisTemplate.opsForValue().get(PostRedisKeys.favoriteCount(POST_ID));

            assertThat(relationCount).isEqualTo(concurrency);
            assertThat(outboxCount).isEqualTo(concurrency);
            assertThat(favoriteCount).isEqualTo(concurrency);
            assertThat(shareCount).isZero();
            assertThat(visibleFavoriteCount).isEqualTo(concurrency);
            assertThat(cachedFavoriteCount).isEqualTo(String.valueOf(concurrency));
        } finally {
            executor.shutdownNow();
        }
    }

    private void insertPublishedPost() {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    id, owner_id, owner_name, owner_profile_version, title, excerpt,
                    status, write_state, published_at, created_at, updated_at, is_archived, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                POST_ID,
                AUTHOR_ID,
                "author",
                1L,
                "favorite-concurrency-post",
                "test-excerpt",
                1,
                "PUBLISHED",
                Timestamp.valueOf(now.minusMinutes(5).toLocalDateTime()),
                Timestamp.valueOf(now.minusHours(1).toLocalDateTime()),
                Timestamp.valueOf(now.toLocalDateTime()),
                false,
                9L
        );
    }
}
