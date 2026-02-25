package com.zhicore.content.infrastructure.health;

import com.mongodb.client.MongoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * MongoDB 健康检查指示器
 * 用于监控 MongoDB 连接状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoDBHealthIndicator implements HealthIndicator {

    private final MongoClient mongoClient;

    @Override
    public Health health() {
        try {
            // 执行 ping 命令验证连接
            Document pingResult = mongoClient.getDatabase("admin")
                .runCommand(new Document("ping", 1));

            if (pingResult.getDouble("ok") == 1.0) {
                return Health.up()
                    .withDetail("database", "MongoDB")
                    .withDetail("status", "Connected")
                    .withDetail("ping", "OK")
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", "MongoDB")
                    .withDetail("status", "Ping failed")
                    .withDetail("result", pingResult.toJson())
                    .build();
            }
        } catch (Exception e) {
            log.error("MongoDB health check failed", e);
            return Health.down()
                .withDetail("database", "MongoDB")
                .withDetail("status", "Connection failed")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
