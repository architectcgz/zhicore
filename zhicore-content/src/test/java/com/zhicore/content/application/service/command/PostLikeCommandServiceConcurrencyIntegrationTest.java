package com.zhicore.content.application.service.command;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.application.service.query.PostLikeQueryService;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@DisplayName("PostLikeCommandService 并发集成测试")
class PostLikeCommandServiceConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final long POST_ID = 90001L;
    private static final long USER_ID = 91001L;
    private static final long AUTHOR_ID = 92001L;

    @Autowired
    private PostLikeCommandService postLikeCommandService;

    @Autowired
    private PostLikeQueryService postLikeQueryService;

    @MockBean
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @BeforeEach
    void setUp() {
        cleanupRedis();
        cleanupPostgres();
        insertPublishedPost();
    }

    @Test
    @DisplayName("同一用户并发点赞时只允许一条关系记录、一条事件和一次计数")
    void concurrentDuplicateLikeShouldKeepDbRedisAndOutboxConsistent() throws Exception {
        int concurrency = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch idGeneratorBarrier = new CountDownLatch(concurrency);
        AtomicLong idSequence = new AtomicLong(1_500_000L);

        when(idGeneratorFeignClient.generateSnowflakeId()).thenAnswer(invocation -> {
            idGeneratorBarrier.countDown();
            assertTrue(idGeneratorBarrier.await(5, TimeUnit.SECONDS), "并发线程未同时进入发号点");
            return ApiResponse.success(idSequence.incrementAndGet());
        });

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                futures.add(executor.submit(invokeLike(startLatch)));
            }

            startLatch.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            long relationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post_likes WHERE post_id = ? AND user_id = ?",
                    Long.class,
                    POST_ID,
                    USER_ID
            );
            long outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?",
                    Long.class,
                    POST_ID,
                    "com.zhicore.integration.messaging.post.PostLikedIntegrationEvent"
            );
            Integer postStatsLikeCount = jdbcTemplate.queryForObject(
                    "SELECT like_count FROM post_stats WHERE post_id = ?",
                    Integer.class,
                    POST_ID
            );
            int visibleLikeCount = postLikeQueryService.getLikeCount(POST_ID);
            Object cachedLikeCount = redisTemplate.opsForValue().get(PostRedisKeys.likeCount(POST_ID));

            assertThat(results).containsExactlyInAnyOrder("SUCCESS", "BUSINESS:已经点赞过了");
            assertThat(relationCount).isEqualTo(1L);
            assertThat(outboxCount).isEqualTo(1L);
            assertThat(postStatsLikeCount).isEqualTo(1);
            assertThat(visibleLikeCount).isEqualTo(1);
            assertThat(cachedLikeCount).isEqualTo("1");
            assertThat(postLikeQueryService.isLiked(USER_ID, POST_ID)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("多个用户并发点赞时 post_stats.like_count 与关系表保持一致")
    void concurrentMultiUserLikeShouldKeepPostStatsConsistent() throws Exception {
        int concurrency = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicLong idSequence = new AtomicLong(1_600_000L);

        when(idGeneratorFeignClient.generateSnowflakeId()).thenAnswer(invocation ->
                ApiResponse.success(idSequence.incrementAndGet()));

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                long userId = USER_ID + i;
                futures.add(executor.submit(invokeLike(startLatch, userId)));
            }

            startLatch.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            long relationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post_likes WHERE post_id = ?",
                    Long.class,
                    POST_ID
            );
            long outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = ?",
                    Long.class,
                    POST_ID,
                    "com.zhicore.integration.messaging.post.PostLikedIntegrationEvent"
            );
            Integer postStatsLikeCount = jdbcTemplate.queryForObject(
                    "SELECT like_count FROM post_stats WHERE post_id = ?",
                    Integer.class,
                    POST_ID
            );
            int visibleLikeCount = postLikeQueryService.getLikeCount(POST_ID);
            Object cachedLikeCount = redisTemplate.opsForValue().get(PostRedisKeys.likeCount(POST_ID));

            assertThat(results).containsOnly("SUCCESS");
            assertThat(relationCount).isEqualTo(concurrency);
            assertThat(outboxCount).isEqualTo(concurrency);
            assertThat(visibleLikeCount).isEqualTo(concurrency);
            assertThat(cachedLikeCount).isEqualTo(String.valueOf(concurrency));
            assertThat(postStatsLikeCount).isEqualTo(concurrency);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<String> invokeLike(CountDownLatch startLatch) {
        return invokeLike(startLatch, USER_ID);
    }

    private Callable<String> invokeLike(CountDownLatch startLatch, long userId) {
        return () -> {
            assertTrue(startLatch.await(5, TimeUnit.SECONDS), "测试线程未同时启动");
            try {
                postLikeCommandService.likePost(userId, POST_ID);
                return "SUCCESS";
            } catch (BusinessException e) {
                return "BUSINESS:" + e.getMessage();
            }
        };
    }

    private void insertPublishedPost() {
        LocalDateTime now = LocalDateTime.now();
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
                "concurrency-post",
                "test-excerpt",
                1,
                "PUBLISHED",
                Timestamp.valueOf(now.minusMinutes(5)),
                Timestamp.valueOf(now.minusHours(1)),
                Timestamp.valueOf(now),
                false,
                7L
        );
    }
}
