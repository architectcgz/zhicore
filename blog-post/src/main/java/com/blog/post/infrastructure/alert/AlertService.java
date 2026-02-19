package com.blog.post.infrastructure.alert;

import com.blog.post.infrastructure.config.AlertProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 告警服务
 * 负责触发和管理各种告警
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertNotifier alertNotifier;
    private final AlertProperties alertProperties;
    
    // 告警去重缓存（防止短时间内重复告警）
    private final ConcurrentMap<String, LocalDateTime> alertCache = new ConcurrentHashMap<>();

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
                    .timestamp(LocalDateTime.now())
                    .resourceId(postId)
                    .sent(false)
                    .build();
            
            sendAlert(alert, alertKey);
        }
    }

    /**
     * 触发 MongoDB 连接失败告警
     *
     * @param errorMessage 错误信息
     */
    public void alertMongoConnectionFailure(String errorMessage) {
        String alertKey = AlertType.MONGODB_CONNECTION_FAILURE.toString();
        
        if (shouldSendAlert(alertKey)) {
            Alert alert = Alert.builder()
                    .id(UUID.randomUUID().toString())
                    .type(AlertType.MONGODB_CONNECTION_FAILURE)
                    .level(AlertType.MONGODB_CONNECTION_FAILURE.getLevel())
                    .title("MongoDB 连接失败")
                    .message("无法连接到 MongoDB 数据库，系统已降级为仅使用 PostgreSQL")
                    .details(errorMessage)
                    .timestamp(LocalDateTime.now())
                    .sent(false)
                    .build();
            
            sendAlert(alert, alertKey);
        }
    }

    /**
     * 触发 PostgreSQL 连接失败告警
     *
     * @param errorMessage 错误信息
     */
    public void alertPostgresConnectionFailure(String errorMessage) {
        String alertKey = AlertType.POSTGRES_CONNECTION_FAILURE.toString();
        
        if (shouldSendAlert(alertKey)) {
            Alert alert = Alert.builder()
                    .id(UUID.randomUUID().toString())
                    .type(AlertType.POSTGRES_CONNECTION_FAILURE)
                    .level(AlertType.POSTGRES_CONNECTION_FAILURE.getLevel())
                    .title("PostgreSQL 连接失败")
                    .message("无法连接到 PostgreSQL 数据库，系统服务不可用")
                    .details(errorMessage)
                    .timestamp(LocalDateTime.now())
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
                    .timestamp(LocalDateTime.now())
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
                    .timestamp(LocalDateTime.now())
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
                    .timestamp(LocalDateTime.now())
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
                    .timestamp(LocalDateTime.now())
                    .sent(false)
                    .build();
            
            sendAlert(alert, alertKey);
        }
    }

    /**
     * 判断是否应该发送告警（去重）
     *
     * @param alertKey 告警键
     * @return 是否应该发送
     */
    private boolean shouldSendAlert(String alertKey) {
        LocalDateTime lastAlertTime = alertCache.get(alertKey);
        LocalDateTime now = LocalDateTime.now();
        
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
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(alertProperties.getDedupWindowMinutes());
        alertCache.entrySet().removeIf(entry -> entry.getValue().isBefore(expiryTime));
        log.debug("Cleaned expired alert cache entries");
    }
}
