package com.blog.post.infrastructure.monitoring;

import com.blog.post.infrastructure.alert.AlertService;
import com.blog.post.infrastructure.config.MonitoringProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 指标监控调度器
 * 定期检查监控指标并触发告警
 * 
 * 使用 @ConfigurationProperties 支持配置动态刷新
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsMonitorScheduler {

    private final MetricsCollector metricsCollector;
    private final AlertService alertService;
    private final MonitoringProperties monitoringProperties;

    /**
     * 每分钟检查一次监控指标
     */
    @Scheduled(fixedRate = 60000) // 60秒
    public void checkMetrics() {
        try {
            log.debug("Starting metrics check...");

            // 检查查询性能
            checkQueryPerformance();

            // 检查双写失败率
            checkDualWriteFailureRate();

            // 清理过期的告警缓存
            alertService.cleanExpiredAlertCache();

            log.debug("Metrics check completed");

        } catch (Exception e) {
            log.error("Error during metrics check", e);
        }
    }

    /**
     * 检查查询性能
     */
    private void checkQueryPerformance() {
        // 检查 PostgreSQL 查询性能
        double postgresAvgTime = metricsCollector.getPostgresAvgQueryTime();
        if (postgresAvgTime > monitoringProperties.getQueryPerformanceThreshold()) {
            log.warn("PostgreSQL query performance degradation detected: avgTime={}ms, threshold={}ms",
                    postgresAvgTime, monitoringProperties.getQueryPerformanceThreshold());
            alertService.alertQueryPerformanceDegradation(
                    "PostgreSQL",
                    postgresAvgTime,
                    monitoringProperties.getQueryPerformanceThreshold()
            );
        }

        // 检查 MongoDB 查询性能
        double mongoAvgTime = metricsCollector.getMongoAvgQueryTime();
        if (mongoAvgTime > monitoringProperties.getQueryPerformanceThreshold()) {
            log.warn("MongoDB query performance degradation detected: avgTime={}ms, threshold={}ms",
                    mongoAvgTime, monitoringProperties.getQueryPerformanceThreshold());
            alertService.alertQueryPerformanceDegradation(
                    "MongoDB",
                    mongoAvgTime,
                    monitoringProperties.getQueryPerformanceThreshold()
            );
        }
    }

    /**
     * 检查双写失败率
     */
    private void checkDualWriteFailureRate() {
        double failureRate = metricsCollector.getDualWriteFailureRate();
        if (failureRate > monitoringProperties.getDualWriteFailureRateThreshold()) {
            log.warn("Dual write failure rate is high: failureRate={}, threshold={}",
                    failureRate, monitoringProperties.getDualWriteFailureRateThreshold());
            alertService.alertDualWriteFailureRateHigh(
                    failureRate,
                    monitoringProperties.getDualWriteFailureRateThreshold()
            );
        }
    }

    /**
     * 每小时输出一次监控统计信息
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void logMonitoringStats() {
        try {
            MonitoringStats stats = MonitoringStats.builder()
                    .postgresAvgQueryTime(metricsCollector.getPostgresAvgQueryTime())
                    .mongoAvgQueryTime(metricsCollector.getMongoAvgQueryTime())
                    .postgresSlowQueryCount(metricsCollector.getPostgresSlowQueryCount())
                    .mongoSlowQueryCount(metricsCollector.getMongoSlowQueryCount())
                    .dualWriteSuccessRate(metricsCollector.getDualWriteSuccessRate())
                    .dualWriteFailureRate(metricsCollector.getDualWriteFailureRate())
                    .dataInconsistencyCount(metricsCollector.getDataInconsistencyCount())
                    .build();

            log.info("\n" +
                    "=".repeat(80) + "\n" +
                    "Monitoring Statistics Report\n" +
                    "=".repeat(80) + "\n" +
                    "PostgreSQL:\n" +
                    "  - Avg Query Time: {:.2f}ms\n" +
                    "  - Slow Query Count: {}\n" +
                    "MongoDB:\n" +
                    "  - Avg Query Time: {:.2f}ms\n" +
                    "  - Slow Query Count: {}\n" +
                    "Dual Write:\n" +
                    "  - Success Rate: {:.2f}%\n" +
                    "  - Failure Rate: {:.2f}%\n" +
                    "Data Consistency:\n" +
                    "  - Inconsistency Count: {}\n" +
                    "=".repeat(80),
                    stats.getPostgresAvgQueryTime(),
                    stats.getPostgresSlowQueryCount(),
                    stats.getMongoAvgQueryTime(),
                    stats.getMongoSlowQueryCount(),
                    stats.getDualWriteSuccessRate() * 100,
                    stats.getDualWriteFailureRate() * 100,
                    stats.getDataInconsistencyCount()
            );

        } catch (Exception e) {
            log.error("Error logging monitoring stats", e);
        }
    }
}
