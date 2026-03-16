package com.zhicore.user.integration;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用户服务集成测试基类。
 *
 * <p>统一提供真实 PostgreSQL 与 Redis，避免 H2 与 PostgreSQL 方言差异导致
 * outbox、锁语义、SQL 兼容性等测试结论失真。
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.bootstrap.enabled=false",
        "spring.cloud.sentinel.enabled=false",
        "spring.profiles.active=test",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/schema.sql",
        "spring.data.redis.password=",
        "jwt.secret=test-jwt-secret-for-integration-tests",
        "file-service.client.tenant-id=zhicore-test"
})
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES_CONTAINER =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("zhicore_user_test")
                    .withUsername("test")
                    .withPassword("test");

    protected static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static final AtomicBoolean CONTAINERS_STARTED = new AtomicBoolean(false);

    @MockBean
    protected RocketMQTemplate rocketMQTemplate;

    private static void ensureContainersStarted() {
        if (CONTAINERS_STARTED.compareAndSet(false, true)) {
            POSTGRES_CONTAINER.start();
            REDIS_CONTAINER.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    POSTGRES_CONTAINER.stop();
                } catch (Exception ignored) {
                }
                try {
                    REDIS_CONTAINER.stop();
                } catch (Exception ignored) {
                }
            }, "user-testcontainers-shutdown"));
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        ensureContainersStarted();

        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getFirstMappedPort().toString());
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.redis.redisson.config", () ->
                String.format("singleServerConfig:\n  address: \"redis://%s:%d\"",
                        REDIS_CONTAINER.getHost(),
                        REDIS_CONTAINER.getFirstMappedPort()));
    }
}
