package com.zhicore.content.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 集成测试基类
 * 
 * 使用 Testcontainers 提供真实的基础设施环境：
 * - PostgreSQL: 元数据存储
 * - MongoDB: 内容存储
 * - Redis: 缓存和分布式锁
 * 
 * @author ZhiCore Team
 */
@SpringBootTest(properties = {
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.bootstrap.enabled=false",
    "spring.profiles.active=test",
    "spring.data.redis.password=",
    "jwt.secret=test-jwt-secret-for-integration-tests",
    "file-service.client.tenant-id=zhicore-test",
    "cache.ttl.entity-detail=600",
    "cache.ttl.list=300",
    "cache.ttl.stats=-1",
    "cache.ttl.null-value=60",
    "cache.lock.wait-time=5",
    "cache.lock.lease-time=10",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:db/schema.sql",
    "spring.task.scheduling.enabled=false"
})
@Testcontainers
public abstract class IntegrationTestBase {

    @MockBean
    private RocketMQTemplate rocketMQTemplate;
    
    // PostgreSQL 容器
    protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER = 
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("test_content")
            .withUsername("test")
            .withPassword("test");
    
    // MongoDB 容器
    protected static final MongoDBContainer MONGO_CONTAINER = 
        new MongoDBContainer(DockerImageName.parse("mongo:7.0"))
            .withExposedPorts(27017);
    
    // Redis 容器
    protected static final GenericContainer<?> REDIS_CONTAINER = 
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    /**
     * 说明：Spring 测试上下文会在 @DynamicPropertySource 阶段读取容器端口信息。
     * 因此必须在注册属性前确保容器已启动，否则可能拿到“未启动时的随机端口”，导致连接被拒绝。
     */
    private static final AtomicBoolean CONTAINERS_STARTED = new AtomicBoolean(false);
    
    @Autowired
    protected ObjectMapper objectMapper;
    
    @Autowired(required = false)
    protected MongoTemplate mongoTemplate;
    
    @Autowired(required = false)
    protected RedisTemplate<String, String> redisTemplate;

    @Autowired(required = false)
    protected JdbcTemplate jdbcTemplate;
    
    private static void ensureContainersStarted() {
        if (CONTAINERS_STARTED.compareAndSet(false, true)) {
            POSTGRES_CONTAINER.start();
            MONGO_CONTAINER.start();
            REDIS_CONTAINER.start();

            // 说明：避免使用 @AfterAll（会在每个测试类结束时触发），导致后续测试类复用已停止的容器连接信息。
            // 统一交由 JVM 退出时清理，确保同一 fork 内的多个测试类可以复用同一套容器。
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
            }, "testcontainers-shutdown"));
        }
    }
    
    /**
     * 动态配置测试属性
     * 
     * 将 Testcontainers 的连接信息注入到 Spring 配置中
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        ensureContainersStarted();

        // PostgreSQL 配置
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // MongoDB 配置
        registry.add("spring.data.mongodb.uri", () -> 
            String.format("mongodb://%s:%d/test_content",
                MONGO_CONTAINER.getHost(),
                MONGO_CONTAINER.getFirstMappedPort()));
        
        // Redis 配置
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> 
            REDIS_CONTAINER.getFirstMappedPort().toString());
        registry.add("spring.data.redis.password", () -> "");

        // Redisson 配置
        registry.add("spring.redis.redisson.config", () -> 
            String.format("singleServerConfig:\n  address: \"redis://%s:%d\"",
                REDIS_CONTAINER.getHost(),
                REDIS_CONTAINER.getFirstMappedPort()));
    }
    
    /**
     * 清理 MongoDB 集合
     */
    protected void cleanupMongoDB() {
        if (mongoTemplate != null) {
            mongoTemplate.getCollectionNames().forEach(collection -> {
                if (!collection.startsWith("system.")) {
                    mongoTemplate.dropCollection(collection);
                }
            });
        }
    }
    
    /**
     * 清理 Redis 数据
     */
    protected void cleanupRedis() {
        if (redisTemplate != null) {
            redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();
        }
    }

    /**
     * 清理 PostgreSQL 主要业务表数据（用于容器复用场景）
     *
     * 说明：Testcontainers 容器在同一 JVM 内会被复用，若不清理数据容易导致测试相互污染。
     */
    protected void cleanupPostgres() {
        if (jdbcTemplate == null) {
            return;
        }

        // 注意：TRUNCATE + CASCADE 能自动处理外键依赖（如果后续 schema 引入外键也能工作）
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    post_tags,
                    post_likes,
                    post_favorites,
                    scheduled_publish_event,
                    outbox_retry_audit,
                    outbox_event,
                    consumed_events,
                    tag_stats,
                    post_stats,
                    categories,
                    tags,
                    posts
                RESTART IDENTITY CASCADE
                """);
    }
}
