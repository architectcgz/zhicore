package com.zhicore.ranking.infrastructure.cache;

import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.ranking.application.port.store.RankingMonthlyStore;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.config.RankingCacheProperties;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis 的月榜缓存存储实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRankingMonthlyStore implements RankingMonthlyStore {

    private static final String MONTHLY_RANKING_TYPE = "monthly";

    private final RankingRedisRepository rankingRedisRepository;
    private final CacheStore cacheStore;
    private final RankingCacheProperties cacheProperties;

    @Override
    public CacheResult<List<HotScore>> getMonthlyRanking(int year, int month, int limit) {
        String emptyKey = RankingRedisKeys.emptyCache(MONTHLY_RANKING_TYPE, year, month);
        CacheResult<Boolean> emptyMarker = cacheStore.get(emptyKey, Boolean.class);
        if (emptyMarker.isHit() && Boolean.TRUE.equals(emptyMarker.getValue())) {
            return CacheResult.nullValue();
        }

        List<HotScore> hotScores = rankingRedisRepository.getMonthlyHotPostsWithScore(year, month, limit);
        if (hotScores == null || hotScores.isEmpty()) {
            return CacheResult.miss();
        }
        return CacheResult.hit(hotScores);
    }

    @Override
    public void backfillMonthlyRanking(int year, int month, List<HotScore> hotScores) {
        if (hotScores == null || hotScores.isEmpty()) {
            return;
        }
        try {
            rankingRedisRepository.batchSetScoreAtomic(RankingRedisKeys.monthlyPosts(year, month), hotScores);
            log.debug("月榜回填 Redis 完成: year={}, month={}, count={}", year, month, hotScores.size());
        } catch (Exception e) {
            log.warn("月榜回填 Redis 失败（不影响本次查询结果）: year={}, month={}", year, month, e);
        }
    }

    @Override
    public void cacheEmptyMonthlyRanking(int year, int month) {
        try {
            Duration ttl = Duration.ofSeconds(cacheProperties.getEmptyCacheTtlSeconds());
            cacheStore.set(
                    RankingRedisKeys.emptyCache(MONTHLY_RANKING_TYPE, year, month),
                    Boolean.TRUE,
                    ttl
            );
            log.debug("缓存月榜空结果标记: year={}, month={}, ttl={}s", year, month, ttl.getSeconds());
        } catch (Exception e) {
            log.warn("缓存月榜空结果标记失败: year={}, month={}", year, month, e);
        }
    }

    @Override
    public String monthlyLoadLockKey(int year, int month) {
        return RankingRedisKeys.monthlyLoadLock(year, month);
    }
}
