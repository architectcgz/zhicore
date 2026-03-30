package com.zhicore.content.infrastructure.monitoring;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.content.infrastructure.alert.AlertService;
import com.zhicore.content.infrastructure.config.MonitoringProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MetricsMonitorScheduler Tests")
class MetricsMonitorSchedulerTest {

    @Test
    @DisplayName("checkMetrics should execute under watchdog lock")
    void checkMetricsShouldExecuteUnderWatchdogLock() {
        MetricsCollector metricsCollector = mock(MetricsCollector.class);
        AlertService alertService = mock(AlertService.class);
        DistributedLockExecutor distributedLockExecutor = mock(DistributedLockExecutor.class);
        MonitoringProperties properties = new MonitoringProperties();
        properties.setQueryPerformanceThreshold(100);
        properties.setDualWriteFailureRateThreshold(0.1);

        when(metricsCollector.getPostgresAvgQueryTime()).thenReturn(120.0);
        when(metricsCollector.getMongoAvgQueryTime()).thenReturn(80.0);
        when(metricsCollector.getDualWriteFailureRate()).thenReturn(0.2);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(distributedLockExecutor).executeWithWatchdogLock(anyString(), any(Runnable.class));

        MetricsMonitorScheduler scheduler = new MetricsMonitorScheduler(
                metricsCollector,
                alertService,
                properties,
                distributedLockExecutor
        );

        scheduler.checkMetrics();

        verify(distributedLockExecutor).executeWithWatchdogLock(eq(MetricsMonitorScheduler.METRICS_CHECK_LOCK_KEY), any(Runnable.class));
        verify(alertService).alertQueryPerformanceDegradation("PostgreSQL", 120.0, 100.0);
        verify(alertService).alertDualWriteFailureRateHigh(0.2, 0.1);
        verify(alertService).cleanExpiredAlertCache();
    }

    @Test
    @DisplayName("logMonitoringStats should execute under watchdog lock")
    void logMonitoringStatsShouldExecuteUnderWatchdogLock() {
        MetricsCollector metricsCollector = mock(MetricsCollector.class);
        AlertService alertService = mock(AlertService.class);
        DistributedLockExecutor distributedLockExecutor = mock(DistributedLockExecutor.class);
        MonitoringProperties properties = new MonitoringProperties();
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(distributedLockExecutor).executeWithWatchdogLock(anyString(), any(Runnable.class));

        MetricsMonitorScheduler scheduler = new MetricsMonitorScheduler(
                metricsCollector,
                alertService,
                properties,
                distributedLockExecutor
        );

        scheduler.logMonitoringStats();

        verify(distributedLockExecutor).executeWithWatchdogLock(eq(MetricsMonitorScheduler.MONITORING_STATS_LOCK_KEY), any(Runnable.class));
    }

    @Test
    @DisplayName("checkMetrics should not invoke alert logic when lock is not acquired")
    void checkMetricsShouldNotInvokeAlertLogicWhenLockNotAcquired() {
        MetricsCollector metricsCollector = mock(MetricsCollector.class);
        AlertService alertService = mock(AlertService.class);
        DistributedLockExecutor distributedLockExecutor = mock(DistributedLockExecutor.class);
        MonitoringProperties properties = new MonitoringProperties();

        MetricsMonitorScheduler scheduler = new MetricsMonitorScheduler(
                metricsCollector,
                alertService,
                properties,
                distributedLockExecutor
        );

        scheduler.checkMetrics();

        verify(distributedLockExecutor).executeWithWatchdogLock(eq(MetricsMonitorScheduler.METRICS_CHECK_LOCK_KEY), any(Runnable.class));
        verify(alertService, never()).cleanExpiredAlertCache();
    }
}
