package com.zhicore.content.infrastructure.alert;

import com.zhicore.content.application.port.alert.ContentAlertPort;
import com.zhicore.content.infrastructure.config.AlertProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 告警服务
 * 负责触发和管理各种告警
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService implements ContentAlertPort {

    private final AlertNotifier alertNotifier;
    private final AlertProperties alertProperties;
    
    // 告警去重缓存（防止短时间内重复告警）
    private final ConcurrentMap<String, OffsetDateTime> alertCache = new ConcurrentHashMap<>();

    /**
     * Outbox 失败告警限流：按 eventType 每分钟最多 N 条
     */
    private final ConcurrentMap<String, MinuteBucket> outboxFailureRate = new ConcurrentHashMap<>();

    /**
     * 触发数据不一致告警
     *
     * @param postId 文章ID
     * @param details 不一致详情
     */
    public void alertDataInconsistency(String postId, String details) {
        String alertKey = AlertType.DATA_INCONSISTENCY + ":" + postId;
        
        if (shouldSendAlert(alertKey)) {
            Alert alert = Alert.builder()
                    .id(UUID.randomUUID().toString())
                    .type(AlertType.DATA_INCONSISTENCY)
                    .level(AlertType.DATA_INCONSISTENCY.getLevel())
                    .title("数据不一致告警")
                    .message(String.format("文章 %s 的数据在 PostgreSQL 和 MongoDB 之间不一致", postId))
                    .details(details)
                    .timestamp(OffsetDateTime.now())
                    .resourceId(postId)
                    .sent(false)
                    .build();
            
            sendAlert(alert, alertKey);
        }
    }

    /**
     * 触发查询性能下降告警
     *
     * @param database 数据库类型（PostgreSQL/MongoDB）
     * @param avgQueryTime 平均查询时间（毫秒）
     * @param threshold 阈值（毫秒）
     */
    public void alertQueryPerformanceDegradation(String database, double avgQueryTime, double threshold) {
        String alertKey = AlertType.QUERY_PERFORMANCE_DEGRADATION + ":" + database;
        
        if (shouldSendAlert(alertKey)) {
            Alert alert = Alert.builder()
                    .id(UUID.randomUUID().toString())
                    .type(AlertType.QUERY_PERFORMANCE_DEGRADATION)
                    .level(AlertType.QUERY_PERFORMANCE_DEGRADATION.getLevel())
                    .title("查询性能下降")
                    .message(String.format("%s 查询性能下降，平均查询时间 %.2fms 超过阈值 %.2fms", 
                            database, avgQueryTime, threshold))
                    .details(String.format("当前平均查询时间: %.2fms, 阈值: %.2fms", avgQueryTime, threshold))
                    .timestamp(OffsetDateTime.now())
                    .sent(false)
                    .build();
            
            sendAlert(alert, alertKey);
        }
    }

    /**
     * 触发存储空间不足告警
     *
     * @param database 数据库类型
     * @param usedPercentage 已使用百分比
     * @param threshold 阈值百分比
     */
    public void alertStorageSpaceLow(String database, double usedPercentage, double threshold) {
        String alertKey = AlertType.STORAGE_SPACE_LOW + ":" + database;
        
        if (shouldSendAlert(alertKey)) {
            Alert alert = Alert.builder()
                    .id(UUID.randomUUID().toString())
                    .type(AlertType.STORAGE_SPACE_LOW)
                    .level(AlertType.STORAGE_SPACE_LOW.getLevel())
                    .title("存储空间不足")
                    .message(String.format("%s 存储空间不足，已使用 %.2f%% 超过阈值 %.2f%%", 
                            database, usedPercentage, threshold))
                    .details(String.format("当前使用率: %.2f%%, 阈值: %.2f%%, 建议清理或扩容", 
                            usedPercentage, threshold))
                    .timestamp(OffsetDateTime.now())
                    .sent(false)
                    .build();
            
            sendAlert(alert, alertKey);
        }
    }

    /**
     * 触发双写失败率过高告警
     *
     * @param failureRate 失败率（0-1之间）
     * @param threshold 阈值（0-1之间）
     */
    public void alertDualWriteFailureRateHigh(double failureRate, double threshold) {
        String alertKey = AlertType.DUAL_WRITE_FAILURE_RATE_HIGH.toString();
        
        if (shouldSendAlert(alertKey)) {
            Alert alert = Alert.builder()
                    .id(UUID.randomUUID().toString())
                    .type(AlertType.DUAL_WRITE_FAILURE_RATE_HIGH)
                    .level(AlertType.DUAL_WRITE_FAILURE_RATE_HIGH.getLevel())
                    .title("双写失败率过高")
                    .message(String.format("双写失败率 %.2f%% 超过阈值 %.2f%%", 
                            failureRate * 100, threshold * 100))
                    .details(String.format("当前失败率: %.2f%%, 阈值: %.2f%%, 请检查数据库连接和性能", 
                            failureRate * 100, threshold * 100))
                    .timestamp(OffsetDateTime.now())
                    .sent(false)
                    .build();
            
            sendAlert(alert, alertKey);
        }
    }

    /**
     * 触发慢查询告警
     *
     * @param database 数据库类型
     * @param operation 操作类型
     * @param duration 查询耗时（毫秒）
     */
    public void alertSlowQuery(String database, String operation, long duration) {
        String alertKey = AlertType.SLOW_QUERY + ":" + database + ":" + operation;
        
        if (shouldSendAlert(alertKey)) {
            Alert alert = Alert.builder()
                    .id(UUID.randomUUID().toString())
                    .type(AlertType.SLOW_QUERY)
                    .level(AlertType.SLOW_QUERY.getLevel())
                    .title("慢查询告警")
                    .message(String.format("%s 慢查询: %s 耗时 %dms", database, operation, duration))
                    .details(String.format("数据库: %s, 操作: %s, 耗时: %dms", database, operation, duration))
                    .timestamp(OffsetDateTime.now())
                    .sent(false)
                    .build();
            
            sendAlert(alert, alertKey);
        }
    }

    /**
     * Outbox 投递失败告警（R14）
     *
     * 触发时机：Outbox 事件达到最大重试次数并收敛为 DEAD。
     *
     * 限流：每 eventType 每分钟最多 10 条，避免异常风暴导致告警通道被打爆。
     */
    public void alertOutboxDispatchFailed(String eventId, String eventType, Long aggregateId, Integer retryCount, String errorMessage) {
        String safeEventType = (eventType == null || eventType.isBlank()) ? "UNKNOWN" : eventType;

        if (!tryAcquireOutboxFailureRate(safeEventType, 10)) {
            log.debug("Outbox 失败告警被限流: eventType={}, eventId={}", safeEventType, eventId);
            return;
        }

        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .type(AlertType.OUTBOX_DISPATCH_FAILED)
                .level(AlertType.OUTBOX_DISPATCH_FAILED.getLevel())
                .title("Outbox 投递失败（重试耗尽）")
                .message("Outbox 事件投递失败，已达到最大重试次数，需人工介入处理")
                .details(String.format(
                        "event_id=%s, event_type=%s, aggregate_id=%s, retry_count=%s, error_message=%s",
                        eventId,
                        safeEventType,
                        aggregateId,
                        retryCount,
                        errorMessage
                ))
                .timestamp(OffsetDateTime.now())
                .resourceId(eventId)
                .sent(false)
                .build();

        sendAlert(alert, AlertType.OUTBOX_DISPATCH_FAILED + ":" + safeEventType + ":" + eventId);
    }

    /**
     * 定时发布失败（重试耗尽）告警。
     *
     * <p>目前复用 {@link AlertType#OUTBOX_DISPATCH_FAILED} 类型，语义为“需要人工介入处理的异步投递/执行失败”。
     * 触发时机：定时发布执行在 publish 重试耗尽后仍失败。
     */
    public void alertScheduledPublishFailedAfterRetries(Long postId, String lastError, Integer retryCount) {
        String safePostId = postId != null ? String.valueOf(postId) : "UNKNOWN";
        String alertKey = AlertType.OUTBOX_DISPATCH_FAILED + ":SCHEDULED_PUBLISH:" + safePostId;

        if (!shouldSendAlert(alertKey)) {
            return;
        }

        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .type(AlertType.OUTBOX_DISPATCH_FAILED)
                .level(AlertType.OUTBOX_DISPATCH_FAILED.getLevel())
                .title("定时发布失败（重试耗尽）")
                .message("定时发布失败且达到重试上限: postId=" + safePostId)
                .details(String.format(
                        "post_id=%s, retry_count=%s, last_error=%s",
                        safePostId,
                        retryCount,
                        lastError
                ))
                .timestamp(OffsetDateTime.now())
                .resourceId(safePostId)
                .sent(false)
                .build();

        sendAlert(alert, alertKey);
    }

    /**
     * 删除文章时正文图片清理失败告警（异步清理，不阻塞主流程）。
     */
    public void alertContentImageCleanupFailed(Long postId, String imageUrl, String errorMessage) {
        String safePostId = postId != null ? String.valueOf(postId) : "UNKNOWN";
        String safeUrl = imageUrl != null ? imageUrl : "UNKNOWN";
        String urlHash = Integer.toHexString(safeUrl.hashCode());
        String alertKey = AlertType.CONTENT_IMAGE_CLEANUP_FAILED + ":" + safePostId + ":" + urlHash;

        if (!shouldSendAlert(alertKey)) {
            return;
        }

        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .type(AlertType.CONTENT_IMAGE_CLEANUP_FAILED)
                .level(AlertType.CONTENT_IMAGE_CLEANUP_FAILED.getLevel())
                .title("正文图片清理失败")
                .message("删除文章时正文图片清理失败: postId=" + safePostId)
                .details(String.format(
                        "post_id=%s, image_url=%s, error_message=%s",
                        safePostId,
                        safeUrl,
                        errorMessage
                ))
                .timestamp(OffsetDateTime.now())
                .resourceId(safePostId)
                .sent(false)
                .build();

        sendAlert(alert, alertKey);
    }

    private boolean tryAcquireOutboxFailureRate(String eventType, int maxPerMinute) {
        long currentMinute = ZonedDateTime.now(ZoneId.systemDefault()).toEpochSecond() / 60;

        MinuteBucket bucket = outboxFailureRate.compute(eventType, (k, existing) -> {
            if (existing == null || existing.minute != currentMinute) {
                return new MinuteBucket(currentMinute);
            }
            return existing;
        });

        int current = bucket.count.incrementAndGet();
        return current <= maxPerMinute;
    }

    private static final class MinuteBucket {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger(0);

        private MinuteBucket(long minute) {
            this.minute = minute;
        }
    }

    /**
     * 触发存储空间不足告警（按“数据库大小（字节）”阈值判断）。
     *
     * <p>用于 {@code StorageMonitor} 定期检查数据库大小场景。
     */
    public void alertStorageSizeExceeded(String database, long sizeBytes, long thresholdBytes, String extraDetails) {
        String alertKey = AlertType.STORAGE_SPACE_LOW + ":" + database + ":bytes";

        if (!shouldSendAlert(alertKey)) {
            return;
        }

        String details = String.format(
                "db=%s, db_size_bytes=%s, threshold_bytes=%s%s",
                database,
                sizeBytes,
                thresholdBytes,
                (extraDetails != null && !extraDetails.isBlank()) ? (", " + extraDetails) : ""
        );

        Alert alert = Alert.builder()
                .id(UUID.randomUUID().toString())
                .type(AlertType.STORAGE_SPACE_LOW)
                .level(AlertType.STORAGE_SPACE_LOW.getLevel())
                .title("存储空间不足")
                .message(String.format("%s 存储空间不足：数据库大小超过阈值", database))
                .details(details)
                .timestamp(OffsetDateTime.now())
                .resourceId(database)
                .sent(false)
                .build();

        sendAlert(alert, alertKey);
    }

    /**
     * 判断是否应该发送告警（去重）
     *
     * @param alertKey 告警键
     * @return 是否应该发送
     */
    private boolean shouldSendAlert(String alertKey) {
        OffsetDateTime lastAlertTime = alertCache.get(alertKey);
        OffsetDateTime now = OffsetDateTime.now();
        
        if (lastAlertTime == null || 
            lastAlertTime.plusMinutes(alertProperties.getDedupWindowMinutes()).isBefore(now)) {
            return true;
        }
        
        log.debug("Alert suppressed due to deduplication: {}", alertKey);
        return false;
    }

    /**
     * 发送告警
     *
     * @param alert 告警信息
     * @param alertKey 告警键（用于去重）
     */
    private void sendAlert(Alert alert, String alertKey) {
        try {
            log.warn("Sending alert: type={}, level={}, message={}", 
                    alert.getType(), alert.getLevel(), alert.getMessage());
            
            // 发送告警通知
            alertNotifier.notify(alert);
            
            // 更新告警缓存
            alertCache.put(alertKey, alert.getTimestamp());
            
            // 标记为已发送
            alert.setSent(true);
            
            log.info("Alert sent successfully: id={}, type={}", alert.getId(), alert.getType());
            
        } catch (Exception e) {
            log.error("Failed to send alert: type={}, message={}", 
                    alert.getType(), alert.getMessage(), e);
        }
    }

    /**
     * 清理过期的告警缓存
     */
    public void cleanExpiredAlertCache() {
        OffsetDateTime expiryTime = OffsetDateTime.now().minusMinutes(alertProperties.getDedupWindowMinutes());
        alertCache.entrySet().removeIf(entry -> entry.getValue().isBefore(expiryTime));
        log.debug("Cleaned expired alert cache entries");
    }
}
