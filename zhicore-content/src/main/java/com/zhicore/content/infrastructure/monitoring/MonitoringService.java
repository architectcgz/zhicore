package com.zhicore.content.infrastructure.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * 监控服务
 * 提供便捷的方法来监控数据库操作和双写操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final MetricsCollector metricsCollector;

    /**
     * 监控 PostgreSQL 查询操作
     *
     * @param operation 操作类型
     * @param supplier  查询操作
     * @param <T>       返回类型
     * @return 查询结果
     */
    public <T> T monitorPostgresQuery(String operation, Supplier<T> supplier) {
        Instant start = Instant.now();
        boolean success = false;
        try {
            T result = supplier.get();
            success = true;
            return result;
        } catch (Exception e) {
            log.error("PostgreSQL query failed: operation={}", operation, e);
            throw e;
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            metricsCollector.recordPostgresQuery(duration, success, operation);
        }
    }

    /**
     * 监控 PostgreSQL 查询操作（无返回值）
     *
     * @param operation 操作类型
     * @param runnable  查询操作
     */
    public void monitorPostgresQuery(String operation, Runnable runnable) {
        Instant start = Instant.now();
        boolean success = false;
        try {
            runnable.run();
            success = true;
        } catch (Exception e) {
            log.error("PostgreSQL query failed: operation={}", operation, e);
            throw e;
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            metricsCollector.recordPostgresQuery(duration, success, operation);
        }
    }

    /**
     * 监控 MongoDB 查询操作
     *
     * @param operation 操作类型
     * @param supplier  查询操作
     * @param <T>       返回类型
     * @return 查询结果
     */
    public <T> T monitorMongoQuery(String operation, Supplier<T> supplier) {
        Instant start = Instant.now();
        boolean success = false;
        try {
            T result = supplier.get();
            success = true;
            return result;
        } catch (Exception e) {
            log.error("MongoDB query failed: operation={}", operation, e);
            throw e;
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            metricsCollector.recordMongoQuery(duration, success, operation);
        }
    }

    /**
     * 监控 MongoDB 查询操作（无返回值）
     *
     * @param operation 操作类型
     * @param runnable  查询操作
     */
    public void monitorMongoQuery(String operation, Runnable runnable) {
        Instant start = Instant.now();
        boolean success = false;
        try {
            runnable.run();
            success = true;
        } catch (Exception e) {
            log.error("MongoDB query failed: operation={}", operation, e);
            throw e;
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            metricsCollector.recordMongoQuery(duration, success, operation);
        }
    }

    /**
     * 监控双写操作
     *
     * @param operation 操作类型
     * @param supplier  双写操作
     * @param <T>       返回类型
     * @return 操作结果
     */
    public <T> T monitorDualWrite(String operation, Supplier<T> supplier) {
        Instant start = Instant.now();
        boolean success = false;
        try {
            T result = supplier.get();
            success = true;
            return result;
        } catch (Exception e) {
            log.error("Dual write failed: operation={}", operation, e);
            throw e;
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            metricsCollector.recordDualWrite(duration, success, operation);
        }
    }

    /**
     * 监控双写操作（无返回值）
     *
     * @param operation 操作类型
     * @param runnable  双写操作
     */
    public void monitorDualWrite(String operation, Runnable runnable) {
        Instant start = Instant.now();
        boolean success = false;
        try {
            runnable.run();
            success = true;
        } catch (Exception e) {
            log.error("Dual write failed: operation={}", operation, e);
            throw e;
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            metricsCollector.recordDualWrite(duration, success, operation);
        }
    }

    /**
     * 获取监控统计信息
     *
     * @return 监控统计信息
     */
    public MonitoringStats getStats() {
        return MonitoringStats.builder()
                .postgresAvgQueryTime(metricsCollector.getPostgresAvgQueryTime())
                .mongoAvgQueryTime(metricsCollector.getMongoAvgQueryTime())
                .postgresSlowQueryCount(metricsCollector.getPostgresSlowQueryCount())
                .mongoSlowQueryCount(metricsCollector.getMongoSlowQueryCount())
                .dualWriteSuccessRate(metricsCollector.getDualWriteSuccessRate())
                .dualWriteFailureRate(metricsCollector.getDualWriteFailureRate())
                .dataInconsistencyCount(metricsCollector.getDataInconsistencyCount())
                .build();
    }
}
