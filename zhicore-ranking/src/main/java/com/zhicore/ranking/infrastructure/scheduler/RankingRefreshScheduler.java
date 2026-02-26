package com.zhicore.ranking.infrastructure.scheduler;

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
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class RankingRefreshScheduler {

    private final PostRankingService postRankingService;
    private final CreatorRankingService creatorRankingService;
    private final RankingRedisRepository rankingRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Timer snapshotTimer;

    public RankingRefreshScheduler(PostRankingService postRankingService,
                                   CreatorRankingService creatorRankingService,
                                   RankingRedisRepository rankingRepository,
                                   RedisTemplate<String, Object> redisTemplate,
                                   MeterRegistry meterRegistry) {
        this.postRankingService = postRankingService;
        this.creatorRankingService = creatorRankingService;
        this.rankingRepository = rankingRepository;
        this.redisTemplate = redisTemplate;
        this.snapshotTimer = Timer.builder("ranking.snapshot.duration")
                .description("快照生成耗时")
                .register(meterRegistry);
    }

    /**
     * 每小时刷新热门文章排行
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshHotPosts() {
        log.info("开始每小时热门文章刷新任务...");
        snapshotTimer.record(() -> {
            try {
                cleanupExpiredDailyRankings();
                cleanupExpiredWeeklyRankings();
                log.info("每小时热门文章刷新任务完成");
            } catch (Exception e) {
                log.error("热门文章刷新失败", e);
            }
        });
    }

    /**
     * 每天凌晨2点刷新创作者排行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void refreshCreatorRanking() {
        log.info("开始每日创作者排行刷新任务...");
        long startTime = System.currentTimeMillis();
        try {
            cleanupExpiredCreatorDailyRankings();
            long duration = System.currentTimeMillis() - startTime;
            log.info("每日创作者排行刷新任务完成: 耗时={}ms", duration);
        } catch (Exception e) {
            log.error("创作者排行刷新失败", e);
        }
    }

    /**
     * 每天凌晨3点刷新话题排行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void refreshTopicRanking() {
        log.info("开始每日话题排行刷新任务...");
        long startTime = System.currentTimeMillis();
        try {
            cleanupExpiredTopicDailyRankings();
            long duration = System.currentTimeMillis() - startTime;
            log.info("每日话题排行刷新任务完成: 耗时={}ms", duration);
        } catch (Exception e) {
            log.error("话题排行刷新失败", e);
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