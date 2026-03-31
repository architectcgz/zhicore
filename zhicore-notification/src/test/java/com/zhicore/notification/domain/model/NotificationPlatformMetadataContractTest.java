package com.zhicore.notification.domain.model;

import com.zhicore.notification.infrastructure.repository.po.NotificationPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Notification 平台化元数据合同测试")
class NotificationPlatformMetadataContractTest {

    @Test
    @DisplayName("通知类型应保留现有类型并补充平台化分类与事件编码")
    void shouldKeepLegacyTypesAndExposePlatformMetadata() {
        assertNotNull(NotificationType.valueOf("LIKE"));
        assertNotNull(NotificationType.valueOf("COMMENT"));
        assertNotNull(NotificationType.valueOf("FOLLOW"));
        assertNotNull(NotificationType.valueOf("REPLY"));
        assertNotNull(NotificationType.valueOf("SYSTEM"));
        assertNotNull(NotificationType.valueOf("POST_PUBLISHED"));

        assertEquals(NotificationCategory.INTERACTION, NotificationType.LIKE.getCategory());
        assertEquals("interaction.like", NotificationType.LIKE.getEventCode());
        assertEquals(NotificationCategory.SYSTEM, NotificationType.SYSTEM.getCategory());
        assertEquals("system.notice", NotificationType.SYSTEM.getEventCode());
        assertEquals(NotificationCategory.CONTENT, NotificationType.POST_PUBLISHED.getCategory());
        assertEquals("content.post-published", NotificationType.POST_PUBLISHED.getEventCode());
    }

    @Test
    @DisplayName("通知持久化模型应预留 category/event_code/metadata 字段")
    void shouldReservePlatformMetadataFieldsInNotificationPo() throws Exception {
        assertNotNull(NotificationPO.class.getDeclaredField("category"));
        assertNotNull(NotificationPO.class.getDeclaredField("eventCode"));
        assertNotNull(NotificationPO.class.getDeclaredField("metadata"));
    }

    @Test
    @DisplayName("历史通知恢复时应按类型回填缺失的平台元数据")
    void shouldFallbackLegacyMetadataFromNotificationType() {
        Notification notification = Notification.reconstitute(
                1L, 2L, NotificationType.SYSTEM,
                "INTERACTION", "", null,
                null, null, null, "系统通知",
                false, null, OffsetDateTime.parse("2026-03-27T10:00:00+08:00")
        );

        assertEquals("SYSTEM", notification.getCategory());
        assertEquals("system.notice", notification.getEventCode());
    }

    @Test
    @DisplayName("初始化脚本应回填历史通知的平台元数据")
    void shouldBackfillLegacyNotificationMetadataInInitSql() throws Exception {
        Path dockerSqlPath = Path.of(System.getProperty("user.dir"))
                .resolve("../docker/postgres-init/02-init-tables.sql")
                .normalize();
        Path mergedSqlPath = Path.of(System.getProperty("user.dir"))
                .resolve("../database/init-all-databases.sql")
                .normalize();

        String dockerSql = Files.readString(dockerSqlPath);
        String mergedSql = Files.readString(mergedSqlPath);

        assertTrue(dockerSql.contains("UPDATE notifications"));
        assertTrue(dockerSql.contains("interaction.like"));
        assertTrue(dockerSql.contains("system.notice"));
        assertTrue(dockerSql.contains("idx_notifications_event_code"));

        assertTrue(mergedSql.contains("UPDATE notifications"));
        assertTrue(mergedSql.contains("interaction.like"));
        assertTrue(mergedSql.contains("system.notice"));
        assertTrue(mergedSql.contains("idx_notifications_event_code"));
    }

    @Test
    @DisplayName("初始化脚本应创建通知偏好与免打扰表")
    void shouldCreatePreferenceAndDndTablesInInitSql() throws Exception {
        Path dockerSqlPath = Path.of(System.getProperty("user.dir"))
                .resolve("../docker/postgres-init/02-init-tables.sql")
                .normalize();
        Path mergedSqlPath = Path.of(System.getProperty("user.dir"))
                .resolve("../database/init-all-databases.sql")
                .normalize();

        String dockerSql = Files.readString(dockerSqlPath);
        String mergedSql = Files.readString(mergedSqlPath);

        assertTrue(dockerSql.contains("notification_user_preference"));
        assertTrue(dockerSql.contains("notification_user_dnd"));
        assertTrue(dockerSql.contains("publish_enabled"));
        assertTrue(mergedSql.contains("notification_user_preference"));
        assertTrue(mergedSql.contains("notification_user_dnd"));
        assertTrue(mergedSql.contains("publish_enabled"));
    }

    @Test
    @DisplayName("初始化脚本应创建发布广播 campaign、shard、delivery 表")
    void shouldCreateBroadcastTablesInInitSql() throws Exception {
        Path dockerSqlPath = Path.of(System.getProperty("user.dir"))
                .resolve("../docker/postgres-init/02-init-tables.sql")
                .normalize();
        Path mergedSqlPath = Path.of(System.getProperty("user.dir"))
                .resolve("../database/init-all-databases.sql")
                .normalize();

        String dockerSql = Files.readString(dockerSqlPath);
        String mergedSql = Files.readString(mergedSqlPath);

        assertTrue(dockerSql.contains("notification_campaign"));
        assertTrue(dockerSql.contains("notification_campaign_shard"));
        assertTrue(dockerSql.contains("notification_delivery"));
        assertTrue(dockerSql.contains("trigger_event_id"));
        assertTrue(dockerSql.contains("dedupe_key"));

        assertTrue(mergedSql.contains("notification_campaign"));
        assertTrue(mergedSql.contains("notification_campaign_shard"));
        assertTrue(mergedSql.contains("notification_delivery"));
        assertTrue(mergedSql.contains("idx_notification_campaign_shard_pending"));
    }
}
