package com.zhicore.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 统计类缓存对账任务基类
 * 
 * 用于定期对账 Redis 缓存与数据库中的统计数据，确保数据一致性
 * 
 * 使用场景：
 * - 点赞计数对账
 * - 关注计数对账
 * - 评论计数对账
 *
 * @author ZhiCore Team
 */
@Slf4j
public abstract class StatsReconciliationTask {

    protected final RedisTemplate<String, Object> redisTemplate;
    protected final MeterRegistry meterRegistry;

    /**
     * 对账阈值：Redis 与 DB 差异超过此值触发告警
     */
    protected static final int ALERT_THRESHOLD = 10;

    private final Counter fixedCounter;
    private final Counter alertCounter;

    protected StatsReconciliationTask(RedisTemplate<String, Object> redisTemplate,
                                      MeterRegistry meterRegistry,
                                      String statsType) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.fixedCounter = meterRegistry.counter("stats.reconciliation.fixed", "type", statsType);
        this.alertCounter = meterRegistry.counter("stats.reconciliation.alert", "type", statsType);
    }

    /**
     * 执行对账
     * 
     * @param <T> 统计数据类型
     * @param <ID> ID 类型
     * @param dbStatsLoader 从数据库加载统计数据
     * @param idExtractor 从统计数据中提取 ID
     * @param countExtractor 从统计数据中提取计数
     * @param keyBuilder 构建 Redis Key
     */
    protected <T, ID> ReconciliationResult reconcile(
            Supplier<List<T>> dbStatsLoader,
            Function<T, ID> idExtractor,
            Function<T, Long> countExtractor,
            Function<ID, String> keyBuilder) {
        
        List<T> dbStats = dbStatsLoader.get();
        int fixedCount = 0;
        int alertCount = 0;
        
        for (T stat : dbStats) {
            ID id = idExtractor.apply(stat);
            Long dbCount = countExtractor.apply(stat);
            String key = keyBuilder.apply(id);
            
            Object redisValue = redisTemplate.opsForValue().get(key);
            Long redisCount = redisValue != null ? ((Number) redisValue).longValue() : null;
            
            if (redisCount == null || !redisCount.equals(dbCount)) {
                long diff = Math.abs((redisCount == null ? 0 : redisCount) - dbCount);
                
                // 修复 Redis 数据
                redisTemplate.opsForValue().set(key, dbCount);
                fixedCount++;
                fixedCounter.increment();
                
                // 差异过大触发告警
                if (diff > ALERT_THRESHOLD) {
                    alertCount++;
                    alertCounter.increment();
                    log.warn("统计计数差异过大: key={}, db={}, redis={}, diff={}", 
                            key, dbCount, redisCount, diff);
                } else {
                    log.debug("修复统计计数: key={}, db={}, redis={}", key, dbCount, redisCount);
                }
            }
        }
        
        return new ReconciliationResult(dbStats.size(), fixedCount, alertCount);
    }

    /**
     * 执行多字段对账
     * 
     * @param <T> 统计数据类型
     * @param <ID> ID 类型
     * @param dbStatsLoader 从数据库加载统计数据
     * @param idExtractor 从统计数据中提取 ID
     * @param fieldReconcilers 字段对账器列表
     */
    protected <T, ID> ReconciliationResult reconcileMultiField(
            Supplier<List<T>> dbStatsLoader,
            Function<T, ID> idExtractor,
            List<FieldReconciler<T, ID>> fieldReconcilers) {
        
        List<T> dbStats = dbStatsLoader.get();
        int fixedCount = 0;
        int alertCount = 0;
        
        for (T stat : dbStats) {
            ID id = idExtractor.apply(stat);
            
            for (FieldReconciler<T, ID> reconciler : fieldReconcilers) {
                Long dbCount = reconciler.countExtractor.apply(stat);
                String key = reconciler.keyBuilder.apply(id);
                
                Object redisValue = redisTemplate.opsForValue().get(key);
                Long redisCount = redisValue != null ? ((Number) redisValue).longValue() : null;
                
                if (redisCount == null || !redisCount.equals(dbCount)) {
                    long diff = Math.abs((redisCount == null ? 0 : redisCount) - dbCount);
                    
                    redisTemplate.opsForValue().set(key, dbCount);
                    fixedCount++;
                    fixedCounter.increment();
                    
                    if (diff > ALERT_THRESHOLD) {
                        alertCount++;
                        alertCounter.increment();
                        log.warn("统计计数差异过大: key={}, db={}, redis={}, diff={}", 
                                key, dbCount, redisCount, diff);
                    }
                }
            }
        }
        
        return new ReconciliationResult(dbStats.size(), fixedCount, alertCount);
    }

    /**
     * 重建所有缓存
     * 
     * @param <T> 统计数据类型
     * @param <ID> ID 类型
     * @param dbStatsLoader 从数据库加载统计数据
     * @param cacheWriter 缓存写入器
     */
    protected <T, ID> int rebuildCache(
            Supplier<List<T>> dbStatsLoader,
            BiConsumer<RedisTemplate<String, Object>, T> cacheWriter) {
        
        List<T> dbStats = dbStatsLoader.get();
        
        for (T stat : dbStats) {
            cacheWriter.accept(redisTemplate, stat);
        }
        
        log.info("缓存重建完成: count={}", dbStats.size());
        return dbStats.size();
    }

    /**
     * 字段对账器
     */
    public static class FieldReconciler<T, ID> {
        final Function<T, Long> countExtractor;
        final Function<ID, String> keyBuilder;

        public FieldReconciler(Function<T, Long> countExtractor, Function<ID, String> keyBuilder) {
            this.countExtractor = countExtractor;
            this.keyBuilder = keyBuilder;
        }

        public static <T, ID> FieldReconciler<T, ID> of(
                Function<T, Long> countExtractor, 
                Function<ID, String> keyBuilder) {
            return new FieldReconciler<>(countExtractor, keyBuilder);
        }
    }

    /**
     * 对账结果
     */
    public record ReconciliationResult(int totalCount, int fixedCount, int alertCount) {
        public void log(String statsType) {
            StatsReconciliationTask.log.info("{}对账完成: total={}, fixed={}, alert={}", 
                    statsType, totalCount, fixedCount, alertCount);
        }
    }
}
