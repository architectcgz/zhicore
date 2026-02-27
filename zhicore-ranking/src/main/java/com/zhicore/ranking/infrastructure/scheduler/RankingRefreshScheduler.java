package com.zhicore.ranking.infrastructure.scheduler;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.ranking.application.service.CreatorRankingService;
import com.zhicore.ranking.application.service.PostRankingService;
import com.zhicore.ranking.domain.model.CreatorStats;
import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排行榜定时刷新任务
 *
 * <p>所有定时任务使用 Redisson 分布式锁，确保多实例部署时只有一个实例执行。</p>
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class RankingRefreshScheduler {

    private final PostRankingService postRankingService;
    private final CreatorRankingService creatorRankingService;
    private final RankingRedisRepository rankingRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DistributedLockExecutor lockExecutor;

    private final Timer postSnapshotTimer;
    private final Timer creatorSnapshotTimer;
    private final Timer topicSnapshotTimer;

    /** 分布式锁 key 前缀 */
    private static final String LOCK_PREFIX = "ranking:lock:scheduler:";
    /** 总榜保留的最大成员数 */
    private static final long TOTAL_BOARD_MAX_SIZE = 10000;

    public RankingRefreshScheduler(PostRankingService postRankingService,
                                   CreatorRankingService creatorRankingService,
                                   RankingRedisRepository rankingRepository,
                                   RedisTemplate<String, Object> redisTemplate,
                                   DistributedLockExecutor lockExecutor,
                                   MeterRegistry meterRegistry) {
        this.postRankingService = postRankingService;
        this.creatorRankingService = creatorRankingService;
        this.rankingRepository = rankingRepository;
        this.redisTemplate = redisTemplate;
        this.lockExecutor = lockExecutor;

        this.postSnapshotTimer = Timer.builder("ranking.snapshot.duration")
                .tag("type", "post").register(meterRegistry);
        this.creatorSnapshotTimer = Timer.builder("ranking.snapshot.duration")
                .tag("type", "creator").register(meterRegistry);
        this.topicSnapshotTimer = Timer.builder("ranking.snapshot.duration")
                .tag("type", "topic").register(meterRegistry);
    }

    /**
     * 每小时刷新热门文章排行
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshHotPosts() {
        lockExecutor.executeWithLock(LOCK_PREFIX + "refresh-hot-posts", () ->
            postSnapshotTimer.record(() -> {
                try {
                    cleanupExpiredDailyRankings();
                    cleanupExpiredWeeklyRankings();
                    trimTotalBoards();
                    log.info("每小时热门文章刷新任务完成");
                } catch (Exception e) {
                    log.error("热门文章刷新失败", e);
                }
            })
        );
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void refreshCreatorRanking() {
        lockExecutor.executeWithLock(LOCK_PREFIX + "refresh-creator", () ->
            creatorSnapshotTimer.record(() -> {
                try {
                    cleanupExpiredCreatorDailyRankings();
                    log.info("每日创作者排行刷新任务完成");
                } catch (Exception e) {
                    log.error("创作者排行刷新失败", e);
                }
            })
        );
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void refreshTopicRanking() {
        lockExecutor.executeWithLock(LOCK_PREFIX + "refresh-topic", () ->
            topicSnapshotTimer.record(() -> {
                try {
                    cleanupExpiredTopicDailyRankings();
                    log.info("每日话题排行刷新任务完成");
                } catch (Exception e) {
                    log.error("话题排行刷新失败", e);
                }
            })
        );
    }

    /**
     * 淘汰总榜低分成员，防止 Sorted Set 无限膨胀
     * <p>保留 Top {@value TOTAL_BOARD_MAX_SIZE}，移除分数最低的成员。</p>
     */
    private void trimTotalBoards() {
        long postTrimmed = rankingRepository.trimSortedSet(
                RankingRedisKeys.hotPosts(), TOTAL_BOARD_MAX_SIZE);
        long creatorTrimmed = rankingRepository.trimSortedSet(
                RankingRedisKeys.hotCreators(), TOTAL_BOARD_MAX_SIZE);
        long topicTrimmed = rankingRepository.trimSortedSet(
                RankingRedisKeys.hotTopics(), TOTAL_BOARD_MAX_SIZE);
        if (postTrimmed + creatorTrimmed + topicTrimmed > 0) {
            log.info("总榜淘汰完成: post={}, creator={}, topic={}",
                    postTrimmed, creatorTrimmed, topicTrimmed);
        }
    }

    private void cleanupExpiredDailyRankings() {
        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        for (int i = 0; i < 7; i++) {
            LocalDate date = threeDaysAgo.minusDays(i);
            String key = RankingRedisKeys.dailyPosts(date);
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("已删除过期日榜: {}", key);
            }
        }
    }

    private void cleanupExpiredWeeklyRankings() {
        int currentWeek = RankingRedisKeys.getCurrentWeekNumber();
        for (int i = 3; i < 10; i++) {
            int weekNumber = currentWeek - i;
            if (weekNumber > 0) {
                String key = RankingRedisKeys.weeklyPosts(weekNumber);
                Boolean deleted = redisTemplate.delete(key);
                if (Boolean.TRUE.equals(deleted)) {
                    log.debug("已删除过期周榜: {}", key);
                }
            }
        }
    }

    private void cleanupExpiredCreatorDailyRankings() {
        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        for (int i = 0; i < 7; i++) {
            LocalDate date = threeDaysAgo.minusDays(i);
            String key = RankingRedisKeys.dailyCreators(date);
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("已删除过期创作者日榜: {}", key);
            }
        }
    }

    private void cleanupExpiredTopicDailyRankings() {
        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        for (int i = 0; i < 7; i++) {
            LocalDate date = threeDaysAgo.minusDays(i);
            String key = RankingRedisKeys.dailyTopics(date);
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("已删除过期话题日榜: {}", key);
            }
        }
    }

    /**
     * 手动触发文章热度刷新（用于管理接口）
     */
    public void manualRefreshPostScore(String postId, PostStats stats, LocalDateTime publishedAt) {
        postRankingService.updatePostScore(postId, stats, publishedAt);
        log.info("手动刷新文章热度: postId={}", postId);
    }

    /**
     * 手动触发创作者热度刷新（用于管理接口）
     */
    public void manualRefreshCreatorScore(String userId, CreatorStats stats) {
        creatorRankingService.updateCreatorScore(userId, stats);
        log.info("手动刷新创作者热度: userId={}", userId);
    }
}
