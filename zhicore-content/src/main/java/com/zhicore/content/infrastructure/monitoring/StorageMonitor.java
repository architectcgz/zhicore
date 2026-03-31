package com.zhicore.content.infrastructure.monitoring;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.content.infrastructure.alert.AlertService;
import com.zhicore.content.infrastructure.config.MonitoringProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 存储空间监控器。
 * 定期检查数据库存储空间使用情况。
 *
 * <p>使用看门狗分布式锁避免多实例重复检查和重复告警。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageMonitor {

    public static final String STORAGE_CHECK_LOCK_KEY = "content:monitoring:storage-check:lock";

    private final AlertService alertService;
    private final MonitoringProperties monitoringProperties;
    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;
    private final DistributedLockExecutor distributedLockExecutor;

    /** 每小时检查一次存储空间。 */
    @Scheduled(fixedRate = 3600000)
    public void checkStorageSpace() {
        if (monitoringProperties.getStorage() != null && !monitoringProperties.getStorage().isEnabled()) {
            log.debug("Storage space check disabled by config");
            return;
        }

        distributedLockExecutor.executeWithWatchdogLock(STORAGE_CHECK_LOCK_KEY, this::doCheckStorageSpace);
    }

    private void doCheckStorageSpace() {
        log.debug("Checking storage space...");

        try {
            checkPostgresStorage();
        } catch (Exception e) {
            log.warn("PostgreSQL storage check failed", e);
        }

        try {
            checkMongoStorage();
        } catch (Exception e) {
            log.warn("MongoDB storage check failed", e);
        }

        log.debug("Storage space check completed");
    }

    private void checkPostgresStorage() {
        Long dbSize = jdbcTemplate.queryForObject(
                "SELECT pg_database_size(current_database()) AS db_size",
                Long.class
        );

        if (dbSize == null) {
            log.warn("PostgreSQL storage check returned null db_size");
            return;
        }

        long threshold = monitoringProperties.getStorage() != null
                ? monitoringProperties.getStorage().getPostgresThreshold()
                : 0L;

        log.debug("PostgreSQL db_size_bytes={}, threshold_bytes={}", dbSize, threshold);

        if (threshold > 0 && dbSize > threshold) {
            alertService.alertStorageSizeExceeded("PostgreSQL", dbSize, threshold, null);
        }
    }

    private void checkMongoStorage() {
        Document stats = mongoTemplate.getDb().runCommand(new Document("dbStats", 1));

        long dataSize = toLong(stats.get("dataSize"));
        long storageSize = toLong(stats.get("storageSize"));

        long observedSize = storageSize > 0 ? storageSize : dataSize;
        long threshold = monitoringProperties.getStorage() != null
                ? monitoringProperties.getStorage().getMongoThreshold()
                : 0L;

        log.debug("MongoDB dataSize_bytes={}, storageSize_bytes={}, threshold_bytes={}", dataSize, storageSize, threshold);

        if (threshold > 0 && observedSize > threshold) {
            alertService.alertStorageSizeExceeded(
                    "MongoDB",
                    observedSize,
                    threshold,
                    "dataSizeBytes=" + dataSize + ", storageSizeBytes=" + storageSize
            );
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
