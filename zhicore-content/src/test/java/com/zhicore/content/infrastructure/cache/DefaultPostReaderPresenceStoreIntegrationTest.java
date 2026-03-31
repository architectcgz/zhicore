package com.zhicore.content.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.content.application.dto.PostReaderPresenceSessionSnapshot;
import com.zhicore.content.application.dto.PostReaderPresenceView;
import com.zhicore.content.application.port.store.PostReaderPresenceStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPostReaderPresenceStoreIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static PostReaderPresenceStore postReaderPresenceStore;

    @BeforeAll
    static void setUpRedis() {
        String host = System.getProperty("test.redis.host",
                System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1"));
        int port = Integer.parseInt(System.getProperty("test.redis.port",
                System.getenv().getOrDefault("TEST_REDIS_PORT", "6379")));
        String password = System.getProperty("test.redis.password",
                System.getenv().getOrDefault("TEST_REDIS_PASSWORD",
                        System.getenv().getOrDefault("REDIS_PASSWORD", "redis123456")));

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        if (!password.isBlank()) {
            configuration.setPassword(password);
        }

        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();

        postReaderPresenceStore = new DefaultPostReaderPresenceStore(redisTemplate, new ObjectMapper());
    }

    @AfterAll
    static void tearDownRedis() {
        if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
            redisTemplate.getConnectionFactory()
                    .getConnection()
                    .serverCommands()
                    .flushDb();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void cleanupRedis() {
        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();
    }

    @Test
    @DisplayName("应统计匿名用户且按用户去重头像")
    void shouldCountReadersAndDeduplicateAvatars() {
        long now = System.currentTimeMillis();
        postReaderPresenceStore.touchSession(PostReaderPresenceSessionSnapshot.builder()
                .sessionId("anon-1")
                .postId(1001L)
                .anonymous(true)
                .fingerprint("anon")
                .lastSeenAtEpochMillis(now)
                .expireAtEpochMillis(now + 40_000)
                .build(), Duration.ofSeconds(40));
        postReaderPresenceStore.touchSession(PostReaderPresenceSessionSnapshot.builder()
                .sessionId("user-1")
                .postId(1001L)
                .userId(2001L)
                .anonymous(false)
                .nickname("读者A")
                .avatarUrl("https://cdn/a.png")
                .lastSeenAtEpochMillis(now + 1000)
                .expireAtEpochMillis(now + 40_000)
                .build(), Duration.ofSeconds(40));
        postReaderPresenceStore.touchSession(PostReaderPresenceSessionSnapshot.builder()
                .sessionId("user-2")
                .postId(1001L)
                .userId(2001L)
                .anonymous(false)
                .nickname("读者A")
                .avatarUrl("https://cdn/a.png")
                .lastSeenAtEpochMillis(now + 2000)
                .expireAtEpochMillis(now + 40_000)
                .build(), Duration.ofSeconds(40));

        PostReaderPresenceView view = postReaderPresenceStore.query(1001L, now, 3);

        assertThat(view.getReadingCount()).isEqualTo(3);
        assertThat(view.getAvatars()).hasSize(1);
        assertThat(view.getAvatars().get(0).getUserId()).isEqualTo("2001");
    }

    @Test
    @DisplayName("leave 应删除明细和 zset membership")
    void shouldRemoveSessionOnLeave() {
        long now = System.currentTimeMillis();
        postReaderPresenceStore.touchSession(PostReaderPresenceSessionSnapshot.builder()
                .sessionId("session-remove")
                .postId(1001L)
                .anonymous(true)
                .lastSeenAtEpochMillis(now)
                .expireAtEpochMillis(now + 40_000)
                .build(), Duration.ofSeconds(40));

        postReaderPresenceStore.removeSession(1001L, "session-remove");
        PostReaderPresenceView view = postReaderPresenceStore.query(1001L, now, 3);

        assertThat(view.getReadingCount()).isZero();
        assertThat(redisTemplate.hasKey(PostRedisKeys.presenceSession("session-remove"))).isFalse();
    }
}
