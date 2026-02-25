package com.zhicore.content.infrastructure.validator;

import com.mongodb.client.MongoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * MongoDB 连接验证器
 * 在应用启动时验证 MongoDB 连接和配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoDBConnectionValidator {

    private final MongoClient mongoClient;
    private final MongoTemplate mongoTemplate;

    /**
     * 应用启动后验证 MongoDB 连接
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateConnection() {
        log.info("Validating MongoDB connection...");

        try {
            // 1. 验证连接
            Document pingResult = mongoClient.getDatabase("admin")
                .runCommand(new Document("ping", 1));
            
            if (pingResult.getDouble("ok") != 1.0) {
                throw new RuntimeException("MongoDB ping failed: " + pingResult.toJson());
            }
            log.info("✓ MongoDB connection successful");

            // 2. 验证数据库
            String databaseName = mongoTemplate.getDb().getName();
            log.info("✓ Connected to database: {}", databaseName);

            // 3. 验证集合
            boolean hasCollections = mongoTemplate.getCollectionNames().size() > 0;
            if (hasCollections) {
                log.info("✓ Database collections found: {}", mongoTemplate.getCollectionNames());
            } else {
                log.warn("⚠ No collections found in database. They will be created on first use.");
            }

            // 4. 验证索引（如果集合存在）
            if (mongoTemplate.collectionExists("post_contents")) {
                validateIndexes("post_contents");
            }
            if (mongoTemplate.collectionExists("post_versions")) {
                validateIndexes("post_versions");
            }
            if (mongoTemplate.collectionExists("post_drafts")) {
                validateIndexes("post_drafts");
            }
            if (mongoTemplate.collectionExists("post_archives")) {
                validateIndexes("post_archives");
            }

            log.info("✓ MongoDB validation completed successfully");

        } catch (Exception e) {
            log.error("✗ MongoDB validation failed", e);
            throw new RuntimeException("Failed to validate MongoDB connection", e);
        }
    }

    /**
     * 验证集合的索引
     */
    private void validateIndexes(String collectionName) {
        try {
            var indexes = mongoTemplate.getCollection(collectionName)
                .listIndexes()
                .into(new java.util.ArrayList<>());
            
            log.info("✓ Collection '{}' has {} indexes", collectionName, indexes.size());
            
            // 记录索引详情
            indexes.forEach(index -> {
                String indexName = index.getString("name");
                Document keys = (Document) index.get("key");
                log.debug("  - Index '{}': {}", indexName, keys.toJson());
            });
            
        } catch (Exception e) {
            log.warn("⚠ Failed to validate indexes for collection '{}': {}", 
                collectionName, e.getMessage());
        }
    }
}
