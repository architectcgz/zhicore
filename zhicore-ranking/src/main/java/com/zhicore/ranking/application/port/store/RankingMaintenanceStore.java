package com.zhicore.ranking.application.port.store;

import java.time.LocalDate;

/**
 * 封装排行榜维护型缓存操作，避免调度任务直接依赖 Redis key 与仓储实现。
 */
public interface RankingMaintenanceStore {

    /**
     * 清理过期的文章日榜与周榜。
     *
     * @param referenceDate 参考日期，通常为当前日期
     */
    void cleanupExpiredHotPostRankings(LocalDate referenceDate);

    /**
     * 清理过期的创作者日榜。
     *
     * @param referenceDate 参考日期，通常为当前日期
     */
    void cleanupExpiredCreatorDailyRankings(LocalDate referenceDate);

    /**
     * 清理过期的话题日榜。
     *
     * @param referenceDate 参考日期，通常为当前日期
     */
    void cleanupExpiredTopicDailyRankings(LocalDate referenceDate);

    /**
     * 淘汰总榜低分成员，防止 Sorted Set 无限膨胀。
     *
     * @param maxSize 每个总榜保留的最大成员数
     */
    void trimTotalBoards(long maxSize);
}
