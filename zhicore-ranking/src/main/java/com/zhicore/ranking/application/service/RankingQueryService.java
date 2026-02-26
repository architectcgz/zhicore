package com.zhicore.ranking.application.service;

import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.mongodb.RankingArchive;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    private final PostRankingService postRankingService;
    private final RankingArchiveService archiveService;
    private final RankingRedisRepository rankingRedisRepository;
    private final RedissonClient redissonClient;

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    private static final int MONTHLY_REDIS_TTL_DAYS = 365;
    /** 回填 Redis 时从 MongoDB 拉取的上限，不受查询 limit 限制 */
    private static final int BACKFILL_MAX_SIZE = 1000;

    public RankingQueryService(PostRankingService postRankingService,
                               RankingArchiveService archiveService,
                               RankingRedisRepository rankingRedisRepository,
                               RedissonClient redissonClient,
                               MeterRegistry meterRegistry) {
        this.postRankingService = postRankingService;
        this.archiveService = archiveService;
        this.rankingRedisRepository = rankingRedisRepository;
        this.redissonClient = redissonClient;

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
        boolean inRedisRange = queryDate.isAfter(now.minusDays(MONTHLY_REDIS_TTL_DAYS));

        if (inRedisRange) {
            List<HotScore> result = postRankingService.getMonthlyHotPostsWithScore(year, month, limit);
            if (result != null && !result.isEmpty()) {
                cacheHitCounter.increment();
                return result;
            }
            // Redis 无数据，加分布式锁防击穿，回源 MongoDB 并回填缓存
            cacheMissCounter.increment();
            return loadAndBackfill(year, month, limit);
        } else {
            cacheMissCounter.increment();
            List<RankingArchive> archives = archiveService.getMonthlyArchive("post", year, month, limit);
            return convertToHotScores(archives);
        }
    }

    private List<HotScore> convertToHotScores(List<RankingArchive> archives) {
        return archives.stream()
                .map(archive -> HotScore.builder()
                        .entityId(archive.getEntityId())
                        .score(archive.getScore())
                        .rank(archive.getRank())
                        .updatedAt(archive.getArchivedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 加分布式锁回源 MongoDB 并回填 Redis
     *
     * <p>防击穿：同一月份维度只允许一个请求回源，其余等待锁释放后读 Redis。
     * 回填拉取完整月榜（上限 BACKFILL_MAX_SIZE），不受查询 limit 限制。
     * 使用 Pipeline + RENAME 保证回填原子可见性。</p>
     */
    private List<HotScore> loadAndBackfill(int year, int month, int limit) {
        String lockKey = "ranking:lock:load:monthly:" + year + "-" + month;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，最多等待 5 秒，持有 30 秒自动释放
            boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (acquired) {
                try {
                    // double-check：获取锁后再查一次 Redis，可能已被其他线程回填
                    List<HotScore> cached = postRankingService.getMonthlyHotPostsWithScore(year, month, limit);
                    if (cached != null && !cached.isEmpty()) {
                        return cached;
                    }
                    // 回源 MongoDB，拉取完整月榜（不受查询 limit 限制）
                    log.debug("月榜 Redis 未命中，回源 MongoDB: year={}, month={}", year, month);
                    List<RankingArchive> fullArchives = archiveService.getMonthlyArchive(
                            "post", year, month, BACKFILL_MAX_SIZE);
                    List<HotScore> fullScores = convertToHotScores(fullArchives);
                    backfillRedis(year, month, fullScores);
                    // 按请求 limit 截断返回
                    return fullScores.size() > limit ? fullScores.subList(0, limit) : fullScores;
                } finally {
                    lock.unlock();
                }
            } else {
                // 等锁超时，尝试读 Redis（大概率已被回填）
                List<HotScore> cached = postRankingService.getMonthlyHotPostsWithScore(year, month, limit);
                if (cached != null && !cached.isEmpty()) {
                    return cached;
                }
                // 兜底：直接查 MongoDB 返回，不回填
                log.warn("月榜回填锁等待超时，直接查 MongoDB: year={}, month={}", year, month);
                List<RankingArchive> archives = archiveService.getMonthlyArchive("post", year, month, limit);
                return convertToHotScores(archives);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("月榜回填锁被中断，直接查 MongoDB: year={}, month={}", year, month);
            List<RankingArchive> archives = archiveService.getMonthlyArchive("post", year, month, limit);
            return convertToHotScores(archives);
        }
    }

    /**
     * 回填 Redis 缓存（Pipeline + RENAME 原子写入）
     */
    private void backfillRedis(int year, int month, List<HotScore> hotScores) {
        if (hotScores == null || hotScores.isEmpty()) {
            return;
        }
        try {
            String key = RankingRedisKeys.monthlyPosts(year, month);
            rankingRedisRepository.batchSetScoreAtomic(key, hotScores);
            log.debug("月榜回填 Redis 完成: year={}, month={}, count={}", year, month, hotScores.size());
        } catch (Exception e) {
            log.warn("月榜回填 Redis 失败（不影响本次查询结果）: year={}, month={}", year, month, e);
        }
    }
}
