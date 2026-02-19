package com.blog.ranking.infrastructure.scheduler;

import com.blog.ranking.application.service.CreatorRankingService;
import com.blog.ranking.application.service.PostRankingService;
import com.blog.ranking.domain.model.CreatorStats;
import com.blog.ranking.domain.model.PostStats;
import com.blog.ranking.infrastructure.redis.RankingRedisKeys;
import com.blog.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排行榜定时刷新任务
 *
 * @author Blog Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingRefreshScheduler {

    private final PostRankingService postRankingService;
    private final CreatorRankingService creatorRankingService;
    private final RankingRedisRepository rankingRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 每小时刷新热门文章排行
     * 
     * 执行时间：每小时整点
     * 
     * 注意：实际生产环境中，应该从数据库或其他服务获取活跃文章的统计数据
     * 这里只是清理过期的日榜和周榜数据
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void refreshHotPosts() {
        log.info("Starting hourly hot posts refresh task...");
        long startTime = System.currentTimeMillis();

        try {
            // 清理过期的日榜数据（保留最近2天）
            cleanupExpiredDailyRankings();

            // 清理过期的周榜数据（保留最近2周）
            cleanupExpiredWeeklyRankings();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Completed hourly hot posts refresh task in {}ms", duration);
        } catch (Exception e) {
            log.error("Failed to refresh hot posts", e);
        }
    }

    /**
     * 每天凌晨2点刷新创作者排行
     * 
     * 执行时间：每天凌晨2点
     * 
     * 注意：实际生产环境中，应该从用户服务获取所有创作者的统计数据
     * 然后重新计算热度分数
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void refreshCreatorRanking() {
        log.info("Starting daily creator ranking refresh task...");
        long startTime = System.currentTimeMillis();

        try {
            // 清理过期的创作者日榜数据
            cleanupExpiredCreatorDailyRankings();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Completed daily creator ranking refresh task in {}ms", duration);
        } catch (Exception e) {
            log.error("Failed to refresh creator ranking", e);
        }
    }

    /**
     * 每天凌晨3点刷新话题排行
     * 
     * 执行时间：每天凌晨3点
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void refreshTopicRanking() {
        log.info("Starting daily topic ranking refresh task...");
        long startTime = System.currentTimeMillis();

        try {
            // 清理过期的话题日榜数据
            cleanupExpiredTopicDailyRankings();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Completed daily topic ranking refresh task in {}ms", duration);
        } catch (Exception e) {
            log.error("Failed to refresh topic ranking", e);
        }
    }

    /**
     * 清理过期的文章日榜数据
     */
    private void cleanupExpiredDailyRankings() {
        // 删除3天前的日榜数据
        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        for (int i = 0; i < 7; i++) {
            LocalDate date = threeDaysAgo.minusDays(i);
            String key = RankingRedisKeys.dailyPosts(date);
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Deleted expired daily ranking: {}", key);
            }
        }
    }

    /**
     * 清理过期的文章周榜数据
     */
    private void cleanupExpiredWeeklyRankings() {
        // 删除3周前的周榜数据
        int currentWeek = RankingRedisKeys.getCurrentWeekNumber();
        for (int i = 3; i < 10; i++) {
            int weekNumber = currentWeek - i;
            if (weekNumber > 0) {
                String key = RankingRedisKeys.weeklyPosts(weekNumber);
                Boolean deleted = redisTemplate.delete(key);
                if (Boolean.TRUE.equals(deleted)) {
                    log.debug("Deleted expired weekly ranking: {}", key);
                }
            }
        }
    }

    /**
     * 清理过期的创作者日榜数据
     */
    private void cleanupExpiredCreatorDailyRankings() {
        // 删除3天前的日榜数据
        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        for (int i = 0; i < 7; i++) {
            LocalDate date = threeDaysAgo.minusDays(i);
            String key = RankingRedisKeys.dailyCreators(date);
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Deleted expired creator daily ranking: {}", key);
            }
        }
    }

    /**
     * 清理过期的话题日榜数据
     */
    private void cleanupExpiredTopicDailyRankings() {
        // 删除3天前的日榜数据
        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        for (int i = 0; i < 7; i++) {
            LocalDate date = threeDaysAgo.minusDays(i);
            String key = RankingRedisKeys.dailyTopics(date);
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Deleted expired topic daily ranking: {}", key);
            }
        }
    }

    /**
     * 手动触发文章热度刷新（用于管理接口）
     *
     * @param postId 文章ID
     * @param stats 文章统计数据
     * @param publishedAt 发布时间
     */
    public void manualRefreshPostScore(String postId, PostStats stats, LocalDateTime publishedAt) {
        postRankingService.updatePostScore(postId, stats, publishedAt);
        log.info("Manually refreshed post score: postId={}", postId);
    }

    /**
     * 手动触发创作者热度刷新（用于管理接口）
     *
     * @param userId 用户ID
     * @param stats 创作者统计数据
     */
    public void manualRefreshCreatorScore(String userId, CreatorStats stats) {
        creatorRankingService.updateCreatorScore(userId, stats);
        log.info("Manually refreshed creator score: userId={}", userId);
    }
}
