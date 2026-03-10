package com.zhicore.ranking.application.port.store;

import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.ranking.domain.model.HotScore;

import java.util.List;

/**
 * 月榜缓存存储端口。
 *
 * 封装月榜空结果标记、Redis 读取和回填逻辑，
 * 避免 application 直接依赖 Redis key 与仓储实现。
 */
public interface RankingMonthlyStore {

    CacheResult<List<HotScore>> getMonthlyRanking(int year, int month, int limit);

    void backfillMonthlyRanking(int year, int month, List<HotScore> hotScores);

    void cacheEmptyMonthlyRanking(int year, int month);

    String monthlyLoadLockKey(int year, int month);
}
