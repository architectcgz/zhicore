package com.zhicore.ranking.infrastructure.cache;

import com.zhicore.ranking.application.port.store.RankingMaintenanceStore;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * 基于 Redis 的排行榜维护型 store 实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRankingMaintenanceStore implements RankingMaintenanceStore {

    /** 文章日榜、创作者日榜、话题日榜默认保留最近 3 天。 */
    private static final int DAILY_RETENTION_DAYS = 3;

    /** 每次额外扫描 7 天，确保历史脏数据能够被逐步清走。 */
    private static final int DAILY_CLEANUP_WINDOW_DAYS = 7;

    /** 周榜默认保留最近 3 周。 */
    private static final int WEEKLY_RETENTION_WEEKS = 3;

    /** 每次额外扫描 7 周。 */
    private static final int WEEKLY_CLEANUP_WINDOW_WEEKS = 7;

    private final RankingRedisRepository rankingRepository;

    @Override
    public void cleanupExpiredHotPostRankings(LocalDate referenceDate) {
        cleanupExpiredDailyRankings(referenceDate);
        cleanupExpiredWeeklyRankings(referenceDate);
    }

    @Override
    public void cleanupExpiredCreatorDailyRankings(LocalDate referenceDate) {
        LocalDate retentionStart = referenceDate.minusDays(DAILY_RETENTION_DAYS);
        for (int i = 0; i < DAILY_CLEANUP_WINDOW_DAYS; i++) {
            LocalDate date = retentionStart.minusDays(i);
            deleteKey(RankingRedisKeys.dailyCreators(date), "创作者日榜");
        }
    }

    @Override
    public void cleanupExpiredTopicDailyRankings(LocalDate referenceDate) {
        LocalDate retentionStart = referenceDate.minusDays(DAILY_RETENTION_DAYS);
        for (int i = 0; i < DAILY_CLEANUP_WINDOW_DAYS; i++) {
            LocalDate date = retentionStart.minusDays(i);
            deleteKey(RankingRedisKeys.dailyTopics(date), "话题日榜");
        }
    }

    @Override
    public void trimTotalBoards(long maxSize) {
        long postTrimmed = rankingRepository.trimSortedSet(RankingRedisKeys.hotPosts(), maxSize);
        long creatorTrimmed = rankingRepository.trimSortedSet(RankingRedisKeys.hotCreators(), maxSize);
        long topicTrimmed = rankingRepository.trimSortedSet(RankingRedisKeys.hotTopics(), maxSize);
        if (postTrimmed + creatorTrimmed + topicTrimmed > 0) {
            log.info("总榜淘汰完成: post={}, creator={}, topic={}",
                    postTrimmed, creatorTrimmed, topicTrimmed);
        }
    }

    private void cleanupExpiredDailyRankings(LocalDate referenceDate) {
        LocalDate retentionStart = referenceDate.minusDays(DAILY_RETENTION_DAYS);
        for (int i = 0; i < DAILY_CLEANUP_WINDOW_DAYS; i++) {
            LocalDate date = retentionStart.minusDays(i);
            deleteKey(RankingRedisKeys.dailyPosts(date), "日榜");
        }
    }

    private void cleanupExpiredWeeklyRankings(LocalDate referenceDate) {
        int currentWeek = currentWeekNumber(referenceDate);
        for (int i = WEEKLY_RETENTION_WEEKS; i < WEEKLY_RETENTION_WEEKS + WEEKLY_CLEANUP_WINDOW_WEEKS; i++) {
            int weekNumber = currentWeek - i;
            if (weekNumber > 0) {
                deleteKey(RankingRedisKeys.weeklyPosts(weekNumber), "周榜");
            }
        }
    }

    private void deleteKey(String key, String label) {
        rankingRepository.deleteKey(key);
        log.debug("已清理过期{}: {}", label, key);
    }

    private int currentWeekNumber(LocalDate referenceDate) {
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        return referenceDate.get(weekFields.weekOfWeekBasedYear());
    }
}
