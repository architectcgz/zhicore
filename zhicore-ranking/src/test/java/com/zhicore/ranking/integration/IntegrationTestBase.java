package com.zhicore.ranking.integration;

import com.zhicore.ranking.infrastructure.mq.CommentCreatedRankingConsumer;
import com.zhicore.ranking.infrastructure.mq.CommentDeletedRankingConsumer;
import com.zhicore.ranking.infrastructure.mq.PostFavoritedRankingConsumer;
import com.zhicore.ranking.infrastructure.mq.PostLikedRankingConsumer;
import com.zhicore.ranking.infrastructure.mq.PostUnfavoritedRankingConsumer;
import com.zhicore.ranking.infrastructure.mq.PostUnlikedRankingConsumer;
import com.zhicore.ranking.infrastructure.mq.PostViewedRankingConsumer;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ranking Spring 集成测试基座。
 *
 * <p>提供真实 PostgreSQL、Redis、MongoDB，避免 Redis 仓储测试在
 * Spring 上下文启动时仍退回到 H2 或本地 Mongo。
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.bootstrap.enabled=false",
        "spring.cloud.sentinel.enabled=false",
        "spring.profiles.active=test",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/ranking-schema.sql",
        "spring.data.redis.password=",
        "ranking.scheduler.enabled=false",
        "jwt.secret=test-jwt-secret-for-ranking-32bytes-min"
})
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTestBase {

    @MockBean
    protected CommentCreatedRankingConsumer commentCreatedRankingConsumer;

    @MockBean
    protected CommentDeletedRankingConsumer commentDeletedRankingConsumer;

    @MockBean
    protected PostFavoritedRankingConsumer postFavoritedRankingConsumer;

    @MockBean
    protected PostLikedRankingConsumer postLikedRankingConsumer;

    @MockBean
    protected PostUnfavoritedRankingConsumer postUnfavoritedRankingConsumer;

    @MockBean
    protected PostUnlikedRankingConsumer postUnlikedRankingConsumer;

    @MockBean
    protected PostViewedRankingConsumer postViewedRankingConsumer;

    protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("zhicore_ranking_test")
                    .withUsername("test")
                    .withPassword("test");

    protected static final MongoDBContainer MONGO_CONTAINER =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    protected static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static final AtomicBoolean CONTAINERS_STARTED = new AtomicBoolean(false);

    protected JdbcTemplate jdbcTemplate;
    protected RedisTemplate<String, Object> redisTemplate;
    protected MongoTemplate mongoTemplate;

    private static void ensureContainersStarted() {
        if (CONTAINERS_STARTED.compareAndSet(false, true)) {
            POSTGRES_CONTAINER.start();
            MONGO_CONTAINER.start();
            REDIS_CONTAINER.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    POSTGRES_CONTAINER.stop();
                } catch (Exception ignored) {
                }
                try {
                    MONGO_CONTAINER.stop();
                } catch (Exception ignored) {
                }
                try {
                    REDIS_CONTAINER.stop();
                } catch (Exception ignored) {
                }
            }, "ranking-testcontainers-shutdown"));
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        ensureContainersStarted();

        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.data.mongodb.uri", MONGO_CONTAINER::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> "zhicore_ranking_test");

        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getFirstMappedPort().toString());
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.redis.redisson.config", () ->
                String.format("singleServerConfig:\n  address: \"redis://%s:%d\"",
                        REDIS_CONTAINER.getHost(),
                        REDIS_CONTAINER.getFirstMappedPort()));
    }
}
