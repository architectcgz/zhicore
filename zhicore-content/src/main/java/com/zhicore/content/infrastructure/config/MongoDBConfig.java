package com.zhicore.content.infrastructure.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.concurrent.TimeUnit;

/**
 * MongoDB 配置类
 * 配置 MongoDB 连接、认证和 Repository 扫描
 * 
 * 使用 @ConfigurationProperties 支持配置动态刷新
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableMongoRepositories(basePackages = "com.zhicore.post.infrastructure.repository.mongodb")
public class MongoDBConfig extends AbstractMongoClientConfiguration {

    private final MongoDBProperties mongoProperties;

    @Override
    protected String getDatabaseName() {
        return mongoProperties.getDatabase();
    }

    @Override
    @Bean
    public MongoClient mongoClient() {
        // 构建连接字符串
        String connectionString = String.format(
            "mongodb://%s:%s@%s:%d/%s?authSource=%s",
            mongoProperties.getUsername(), 
            mongoProperties.getPassword(), 
            mongoProperties.getHost(), 
            mongoProperties.getPort(), 
            mongoProperties.getDatabase(), 
            mongoProperties.getAuthenticationDatabase()
        );

        log.info("Initializing MongoDB connection to: mongodb://{}:{}/{}", 
            mongoProperties.getHost(), 
            mongoProperties.getPort(), 
            mongoProperties.getDatabase());

        // 配置 MongoDB 客户端设置
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(connectionString))
            .applyToConnectionPoolSettings(builder -> builder
                .maxSize(mongoProperties.getMaxConnectionPoolSize())
                .minSize(mongoProperties.getMinConnectionPoolSize())
                .maxConnectionIdleTime(60000, TimeUnit.MILLISECONDS)
                .maxConnectionLifeTime(300000, TimeUnit.MILLISECONDS)
            )
            .applyToSocketSettings(builder -> builder
                .connectTimeout(mongoProperties.getConnectionTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(mongoProperties.getSocketTimeout(), TimeUnit.MILLISECONDS)
            )
            .applyToClusterSettings(builder -> builder
                .serverSelectionTimeout(5000, TimeUnit.MILLISECONDS)
            )
            .build();

        return MongoClients.create(settings);
    }

    @Bean
    public MongoTemplate mongoTemplate() throws Exception {
        return new MongoTemplate(mongoClient(), getDatabaseName());
    }
}
