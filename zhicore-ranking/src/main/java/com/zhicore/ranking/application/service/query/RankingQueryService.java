package com.zhicore.ranking.application.service.query;

import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.ranking.application.port.policy.RankingQueryPolicy;
import com.zhicore.ranking.application.port.store.RankingArchiveStore;
import com.zhicore.ranking.application.port.store.RankingMonthlyStore;
import com.zhicore.ranking.domain.model.HotScore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 排行榜查询服务（智能路由）
 *
 * 根据查询日期自动选择数据源：
 * - Redis：TTL 范围内的热数据（缓存命中）
 * - MongoDB：TTL 范围外的冷数据（缓存未命中）
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
public class RankingQueryService {

    private static final String POST_ENTITY_TYPE = "post";

    private final RankingMonthlyStore rankingMonthlyStore;
    private final RankingArchiveStore rankingArchiveStore;
    private final LockManager lockManager;
    private final RankingQueryPolicy rankingQueryPolicy;

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public RankingQueryService(RankingMonthlyStore rankingMonthlyStore,
                               RankingArchiveStore rankingArchiveStore,
                               LockManager lockManager,
                               RankingQueryPolicy rankingQueryPolicy,
                               MeterRegistry meterRegistry) {
        this.rankingMonthlyStore = rankingMonthlyStore;
        this.rankingArchiveStore = rankingArchiveStore;
        this.lockManager = lockManager;
        this.rankingQueryPolicy = rankingQueryPolicy;

        this.cacheHitCounter = Counter.builder("ranking.cache.hit")
                .tag("type", "monthly")
                .description("排行榜缓存命中次数")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("ranking.cache.miss")
                .tag("type", "monthly")
                .description("排行榜缓存未命中次数")
                .register(meterRegistry);
    }

    /**
     * 查询月榜（自动路由到 Redis 或 MongoDB）
     *
     * <p>路由逻辑：TTL 范围内先查 Redis，有数据为 hit，无数据回源 MongoDB 为 miss。
     * TTL 范围外直接查 MongoDB。</p>
     */
    public List<HotScore> getMonthlyRanking(int year, int month, int limit) {
        LocalDate now = LocalDate.now();
        LocalDate queryDate = LocalDate.of(year, month, 1);
        boolean inRedisRange = rankingQueryPolicy.isMonthlyQueryInRedisRange(queryDate, now);

        if (inRedisRange) {
            CacheResult<List<HotScore>> cached = rankingMonthlyStore.getMonthlyRanking(year, month, limit);
            if (cached.isNull()) {
                cacheHitCounter.increment();
                return Collections.emptyList();
            }
            if (cached.isHit()) {
                cacheHitCounter.increment();
                return cached.getValue();
            }
            // Redis 无数据，加分布式锁防击穿，回源 MongoDB 并回填缓存
            cacheMissCounter.increment();
            return loadAndBackfill(year, month, limit);
        } else {
            cacheMissCounter.increment();
            return rankingArchiveStore.getMonthlyArchive(POST_ENTITY_TYPE, year, month, limit);
        }
    }

    /**
     * 加分布式锁回源 MongoDB 并回填 Redis
     *
     * <p>防击穿：同一月份维度只允许一个请求回源，其余等待锁释放后读 Redis。
     * 回填拉取完整月榜（上限 BACKFILL_MAX_SIZE），不受查询 limit 限制。
     * 使用 Pipeline + RENAME 保证回填原子可见性。</p>
     */
    private List<HotScore> loadAndBackfill(int year, int month, int limit) {
        String lockKey = rankingMonthlyStore.monthlyLoadLockKey(year, month);
        boolean acquired = lockManager.tryLock(
                lockKey,
                rankingQueryPolicy.monthlyLoadLockWaitTime(),
                rankingQueryPolicy.monthlyLoadLockLeaseTime()
        );
        if (acquired) {
            try {
                // double-check：获取锁后再查一次 Redis，可能已被其他线程回填
                CacheResult<List<HotScore>> cached = rankingMonthlyStore.getMonthlyRanking(year, month, limit);
                if (cached.isNull()) {
                    return Collections.emptyList();
                }
                if (cached.isHit()) {
                    return cached.getValue();
                }
                // 回源 MongoDB，拉取完整月榜（不受查询 limit 限制）
                log.debug("月榜 Redis 未命中，回源 MongoDB: year={}, month={}", year, month);
                List<HotScore> fullScores = rankingArchiveStore.getMonthlyArchive(
                        POST_ENTITY_TYPE, year, month, rankingQueryPolicy.monthlyBackfillMaxSize());

                // MongoDB 也无数据，缓存空标记 30s 防穿透
                if (fullScores.isEmpty()) {
                    rankingMonthlyStore.cacheEmptyMonthlyRanking(year, month);
                    return Collections.emptyList();
                }

                rankingMonthlyStore.backfillMonthlyRanking(year, month, fullScores);
                // 按请求 limit 截断返回
                return fullScores.size() > limit ? fullScores.subList(0, limit) : fullScores;
            } finally {
                lockManager.unlock(lockKey);
            }
        }

        // 等锁超时或锁系统异常，尝试读 Redis（大概率已被回填）
        CacheResult<List<HotScore>> cached = rankingMonthlyStore.getMonthlyRanking(year, month, limit);
        if (cached.isNull()) {
            return Collections.emptyList();
        }
        if (cached.isHit()) {
            return cached.getValue();
        }
        // 兜底：直接查 MongoDB 返回，不回填
        log.warn("月榜回填锁等待超时或获取失败，直接查 MongoDB: year={}, month={}", year, month);
        return rankingArchiveStore.getMonthlyArchive(POST_ENTITY_TYPE, year, month, limit);
    }
}
