package com.zhicore.content.infrastructure.monitoring;

import com.zhicore.content.infrastructure.config.MonitoringProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 监控指标收集器
 * 使用 Micrometer 收集数据库查询响应时间、双写成功率和失败率、慢查询日志等指标
 * 指标将通过 Prometheus 端点暴露，供 Grafana 展示
 */
@Slf4j
@Component
public class MetricsCollector {

    private final MeterRegistry meterRegistry;
    private final MonitoringProperties monitoringProperties;

    // PostgreSQL 指标
    private final Timer postgresQueryTimer;
    private final Counter postgresQuerySuccessCounter;
    private final Counter postgresQueryFailureCounter;
    private final Counter postgresSlowQueryCounter;

    // MongoDB 指标
    private final Timer mongoQueryTimer;
    private final Counter mongoQuerySuccessCounter;
    private final Counter mongoQueryFailureCounter;
    private final Counter mongoSlowQueryCounter;

    // 双写指标
    private final Counter dualWriteSuccessCounter;
    private final Counter dualWriteFailureCounter;
    private final Timer dualWriteTimer;
    
    // 数据一致性指标
    private final Counter dataInconsistencyCounter;
    private final Counter consistencyCheckCounter;

    public MetricsCollector(MeterRegistry meterRegistry, MonitoringProperties monitoringProperties) {
        this.meterRegistry = meterRegistry;
        this.monitoringProperties = monitoringProperties;

        // 初始化 PostgreSQL 指标
        this.postgresQueryTimer = Timer.builder("post.database.query.duration")
                .tag("database", "postgresql")
                .description("PostgreSQL query duration in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99) // P50, P95, P99
                .register(meterRegistry);

        this.postgresQuerySuccessCounter = Counter.builder("post.database.query.total")
                .tag("database", "postgresql")
                .tag("status", "success")
                .description("PostgreSQL query success count")
                .register(meterRegistry);

        this.postgresQueryFailureCounter = Counter.builder("post.database.query.total")
                .tag("database", "postgresql")
                .tag("status", "failure")
                .description("PostgreSQL query failure count")
                .register(meterRegistry);

        this.postgresSlowQueryCounter = Counter.builder("post.database.query.slow.total")
                .tag("database", "postgresql")
                .description("PostgreSQL slow query count")
                .register(meterRegistry);

        // 初始化 MongoDB 指标
        this.mongoQueryTimer = Timer.builder("post.database.query.duration")
                .tag("database", "mongodb")
                .description("MongoDB query duration in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99) // P50, P95, P99
                .register(meterRegistry);

        this.mongoQuerySuccessCounter = Counter.builder("post.database.query.total")
                .tag("database", "mongodb")
                .tag("status", "success")
                .description("MongoDB query success count")
                .register(meterRegistry);

        this.mongoQueryFailureCounter = Counter.builder("post.database.query.total")
                .tag("database", "mongodb")
                .tag("status", "failure")
                .description("MongoDB query failure count")
                .register(meterRegistry);

        this.mongoSlowQueryCounter = Counter.builder("post.database.query.slow.total")
                .tag("database", "mongodb")
                .description("MongoDB slow query count")
                .register(meterRegistry);

        // 初始化双写指标
        this.dualWriteSuccessCounter = Counter.builder("post.dual.write.total")
                .tag("status", "success")
                .description("Dual write success count")
                .register(meterRegistry);

        this.dualWriteFailureCounter = Counter.builder("post.dual.write.total")
                .tag("status", "failure")
                .description("Dual write failure count")
                .register(meterRegistry);

        this.dualWriteTimer = Timer.builder("post.dual.write.duration")
                .description("Dual write operation duration in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99) // P50, P95, P99
                .register(meterRegistry);
        
        // 初始化数据一致性指标
        this.dataInconsistencyCounter = Counter.builder("post.data.inconsistency.total")
                .description("Data inconsistency detected count")
                .register(meterRegistry);
        
        this.consistencyCheckCounter = Counter.builder("post.consistency.check.total")
                .description("Consistency check performed count")
                .register(meterRegistry);
    }

    /**
     * 记录 PostgreSQL 查询响应时间
     *
     * @param duration 查询耗时
     * @param success  是否成功
     * @param operation 操作类型（如 select, insert, update, delete）
     */
    public void recordPostgresQuery(Duration duration, boolean success, String operation) {
        long durationMs = duration.toMillis();

        // 记录查询时间
        postgresQueryTimer.record(duration);

        // 记录成功/失败
        if (success) {
            postgresQuerySuccessCounter.increment();
        } else {
            postgresQueryFailureCounter.increment();
        }

        // 记录慢查询
        if (durationMs > monitoringProperties.getSlowQueryThresholdMs()) {
            postgresSlowQueryCounter.increment();
            log.warn("[Slow Query] PostgreSQL: operation={}, duration={}ms", operation, durationMs);
        }

        log.debug("PostgreSQL query recorded: operation={}, duration={}ms, success={}", 
                operation, durationMs, success);
    }

    /**
     * 记录 MongoDB 查询响应时间
     *
     * @param duration 查询耗时
     * @param success  是否成功
     * @param operation 操作类型（如 find, insert, update, delete）
     */
    public void recordMongoQuery(Duration duration, boolean success, String operation) {
        long durationMs = duration.toMillis();

        // 记录查询时间
        mongoQueryTimer.record(duration);

        // 记录成功/失败
        if (success) {
            mongoQuerySuccessCounter.increment();
        } else {
            mongoQueryFailureCounter.increment();
        }

        // 记录慢查询
        if (durationMs > monitoringProperties.getSlowQueryThresholdMs()) {
            mongoSlowQueryCounter.increment();
            log.warn("[Slow Query] MongoDB: operation={}, duration={}ms", operation, durationMs);
        }

        log.debug("MongoDB query recorded: operation={}, duration={}ms, success={}", 
                operation, durationMs, success);
    }

    /**
     * 记录双写操作
     *
     * @param duration 操作耗时
     * @param success  是否成功
     * @param operation 操作类型（如 create, update, delete）
     */
    public void recordDualWrite(Duration duration, boolean success, String operation) {
        long durationMs = duration.toMillis();

        // 记录双写时间
        dualWriteTimer.record(duration);

        // 记录成功/失败
        if (success) {
            dualWriteSuccessCounter.increment();
            log.debug("Dual write success: operation={}, duration={}ms", operation, durationMs);
        } else {
            dualWriteFailureCounter.increment();
            log.error("Dual write failure: operation={}, duration={}ms", operation, durationMs);
        }
    }
    
    /**
     * 记录数据不一致
     */
    public void recordDataInconsistency() {
        dataInconsistencyCounter.increment();
        log.error("[Data Inconsistency] Detected data inconsistency between PostgreSQL and MongoDB");
    }
    
    /**
     * 记录一致性检查
     */
    public void recordConsistencyCheck() {
        consistencyCheckCounter.increment();
    }

    /**
     * 获取双写成功率
     *
     * @return 成功率（0-1之间）
     */
    public double getDualWriteSuccessRate() {
        double successCount = dualWriteSuccessCounter.count();
        double failureCount = dualWriteFailureCounter.count();
        double totalCount = successCount + failureCount;

        if (totalCount == 0) {
            return 1.0; // 没有操作时返回100%
        }

        return successCount / totalCount;
    }

    /**
     * 获取双写失败率
     *
     * @return 失败率（0-1之间）
     */
    public double getDualWriteFailureRate() {
        return 1.0 - getDualWriteSuccessRate();
    }

    /**
     * 获取 PostgreSQL 平均查询时间（毫秒）
     *
     * @return 平均查询时间
     */
    public double getPostgresAvgQueryTime() {
        return postgresQueryTimer.mean(TimeUnit.MILLISECONDS);
    }

    /**
     * 获取 MongoDB 平均查询时间（毫秒）
     *
     * @return 平均查询时间
     */
    public double getMongoAvgQueryTime() {
        return mongoQueryTimer.mean(TimeUnit.MILLISECONDS);
    }

    /**
     * 获取 PostgreSQL 慢查询数量
     *
     * @return 慢查询数量
     */
    public long getPostgresSlowQueryCount() {
        return (long) postgresSlowQueryCounter.count();
    }

    /**
     * 获取 MongoDB 慢查询数量
     *
     * @return 慢查询数量
     */
    public long getMongoSlowQueryCount() {
        return (long) mongoSlowQueryCounter.count();
    }
    
    /**
     * 获取数据不一致数量
     *
     * @return 数据不一致数量
     */
    public long getDataInconsistencyCount() {
        return (long) dataInconsistencyCounter.count();
    }
}
