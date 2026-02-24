package com.zhicore.content.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.content.domain.repository.PostFavoriteRepository;
import com.zhicore.content.domain.repository.PostLikeRepository;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
    "spring.sql.init.schema-locations=classpath:db/schema.sql"
})
@Testcontainers
public abstract class IntegrationTestBase {

    @MockBean
    private PostFavoriteRepository postFavoriteRepository;

    @MockBean
    private PostLikeRepository postLikeRepository;

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
    
    @Autowired
    protected ObjectMapper objectMapper;
    
    @Autowired(required = false)
    protected MongoTemplate mongoTemplate;
    
    @Autowired(required = false)
    protected RedisTemplate<String, String> redisTemplate;
    
    @BeforeAll
    static void startContainers() {
        POSTGRES_CONTAINER.start();
        MONGO_CONTAINER.start();
        REDIS_CONTAINER.start();
    }
    
    @AfterAll
    static void stopContainers() {
        POSTGRES_CONTAINER.stop();
        MONGO_CONTAINER.stop();
        REDIS_CONTAINER.stop();
    }
    
    /**
     * 动态配置测试属性
     * 
     * 将 Testcontainers 的连接信息注入到 Spring 配置中
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
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
}
