package com.zhicore.content.infrastructure.monitoring;

import com.mongodb.client.MongoDatabase;
import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.content.infrastructure.alert.AlertService;
import com.zhicore.content.infrastructure.config.MonitoringProperties;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("StorageMonitor Watchdog Lock Tests")
class StorageMonitorWatchdogLockTest {

    @Test
    @DisplayName("checkStorageSpace should execute under watchdog lock")
    void checkStorageSpaceShouldExecuteUnderWatchdogLock() {
        AlertService alertService = mock(AlertService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        DistributedLockExecutor distributedLockExecutor = mock(DistributedLockExecutor.class);

        MonitoringProperties properties = new MonitoringProperties();
        properties.getStorage().setEnabled(true);
        properties.getStorage().setPostgresThreshold(100L);
        properties.getStorage().setMongoThreshold(100L);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(101L);
        when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
        when(mongoDatabase.runCommand(any(Document.class)))
                .thenReturn(new Document("dataSize", 50L).append("storageSize", 101L));
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(distributedLockExecutor).executeWithWatchdogLock(anyString(), any(Runnable.class));

        StorageMonitor monitor = new StorageMonitor(alertService, properties, jdbcTemplate, mongoTemplate, distributedLockExecutor);
        monitor.checkStorageSpace();

        verify(distributedLockExecutor).executeWithWatchdogLock(eq(StorageMonitor.STORAGE_CHECK_LOCK_KEY), any(Runnable.class));
        verify(alertService).alertStorageSizeExceeded(eq("PostgreSQL"), eq(101L), eq(100L), isNull());
        verify(alertService).alertStorageSizeExceeded(
                eq("MongoDB"),
                eq(101L),
                eq(100L),
                argThat(details -> details != null && details.contains("storageSizeBytes=101"))
        );
    }

    @Test
    @DisplayName("checkStorageSpace should return before locking when storage monitoring disabled")
    void checkStorageSpaceShouldReturnBeforeLockingWhenDisabled() {
        AlertService alertService = mock(AlertService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        DistributedLockExecutor distributedLockExecutor = mock(DistributedLockExecutor.class);

        MonitoringProperties properties = new MonitoringProperties();
        properties.getStorage().setEnabled(false);

        StorageMonitor monitor = new StorageMonitor(alertService, properties, jdbcTemplate, mongoTemplate, distributedLockExecutor);
        monitor.checkStorageSpace();

        verify(distributedLockExecutor, never()).executeWithWatchdogLock(anyString(), any(Runnable.class));
        verify(alertService, never()).alertStorageSizeExceeded(anyString(), any(Long.class), any(Long.class), any());
    }
}
