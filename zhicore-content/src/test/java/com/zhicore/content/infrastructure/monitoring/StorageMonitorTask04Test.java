package com.zhicore.content.infrastructure.monitoring;

import com.mongodb.client.MongoDatabase;
import com.zhicore.content.infrastructure.alert.AlertService;
import com.zhicore.content.infrastructure.config.MonitoringProperties;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageMonitorTask04Test {

    @Test
    void postgresSizeAboveThreshold_triggersAlert() {
        AlertService alertService = mock(AlertService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);

        MonitoringProperties properties = new MonitoringProperties();
        properties.getStorage().setEnabled(true);
        properties.getStorage().setPostgresThreshold(100L);
        properties.getStorage().setMongoThreshold(10_000L);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(101L);
        when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
        when(mongoDatabase.runCommand(any(Document.class)))
                .thenReturn(new Document("dataSize", 1L).append("storageSize", 1L));

        StorageMonitor monitor = new StorageMonitor(alertService, properties, jdbcTemplate, mongoTemplate);
        monitor.checkStorageSpace();

        verify(alertService).alertStorageSizeExceeded(eq("PostgreSQL"), eq(101L), eq(100L), isNull());
    }

    @Test
    void mongoSizeAboveThreshold_triggersAlert() {
        AlertService alertService = mock(AlertService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);

        MonitoringProperties properties = new MonitoringProperties();
        properties.getStorage().setEnabled(true);
        properties.getStorage().setPostgresThreshold(10_000L);
        properties.getStorage().setMongoThreshold(100L);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);
        when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
        when(mongoDatabase.runCommand(any(Document.class)))
                .thenReturn(new Document("dataSize", 50L).append("storageSize", 101L));

        StorageMonitor monitor = new StorageMonitor(alertService, properties, jdbcTemplate, mongoTemplate);
        monitor.checkStorageSpace();

        verify(alertService).alertStorageSizeExceeded(
                eq("MongoDB"),
                eq(101L),
                eq(100L),
                argThat(details -> details != null && details.contains("storageSizeBytes=101"))
        );
    }
}
