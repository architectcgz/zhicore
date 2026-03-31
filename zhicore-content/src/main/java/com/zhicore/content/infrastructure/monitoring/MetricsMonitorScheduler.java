package com.zhicore.content.infrastructure.monitoring;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.content.infrastructure.alert.AlertService;
import com.zhicore.content.infrastructure.config.MonitoringProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 指标监控调度器。
 * 定期检查监控指标并触发告警。
 *
 * <p>使用看门狗分布式锁避免多实例重复检查和重复告警。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsMonitorScheduler {

    public static final String METRICS_CHECK_LOCK_KEY = "content:monitoring:metrics-check:lock";
    public static final String MONITORING_STATS_LOCK_KEY = "content:monitoring:stats-log:lock";

    private final MetricsCollector metricsCollector;
    private final AlertService alertService;
    private final MonitoringProperties monitoringProperties;
    private final DistributedLockExecutor distributedLockExecutor;

    /** 每分钟检查一次监控指标。 */
    @Scheduled(fixedRate = 60000)
    public void checkMetrics() {
        distributedLockExecutor.executeWithWatchdogLock(METRICS_CHECK_LOCK_KEY, this::doCheckMetrics);
    }

    private void doCheckMetrics() {
        try {
            log.debug("Starting metrics check...");

            checkQueryPerformance();
            checkDualWriteFailureRate();
            alertService.cleanExpiredAlertCache();

            log.debug("Metrics check completed");
        } catch (Exception e) {
            log.error("Error during metrics check", e);
        }
    }

    private void checkQueryPerformance() {
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

    /** 每小时输出一次监控统计信息。 */
    @Scheduled(fixedRate = 3600000)
    public void logMonitoringStats() {
        distributedLockExecutor.executeWithWatchdogLock(MONITORING_STATS_LOCK_KEY, this::doLogMonitoringStats);
    }

    private void doLogMonitoringStats() {
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
