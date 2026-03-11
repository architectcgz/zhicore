package com.zhicore.ranking.infrastructure.scheduler;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.ranking.application.port.store.RankingMaintenanceStore;
import com.zhicore.ranking.application.service.CreatorRankingService;
import com.zhicore.ranking.application.service.PostRankingService;
import com.zhicore.ranking.domain.model.CreatorStats;
import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
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
    private final RankingMaintenanceStore rankingMaintenanceStore;
    private final DistributedLockExecutor lockExecutor;

    private final Timer postSnapshotTimer;
    private final Timer creatorSnapshotTimer;
    private final Timer topicSnapshotTimer;

    /** 总榜保留的最大成员数 */
    private static final long TOTAL_BOARD_MAX_SIZE = 10000;

    public RankingRefreshScheduler(PostRankingService postRankingService,
                                   CreatorRankingService creatorRankingService,
                                   RankingMaintenanceStore rankingMaintenanceStore,
                                   DistributedLockExecutor lockExecutor,
                                   MeterRegistry meterRegistry) {
        this.postRankingService = postRankingService;
        this.creatorRankingService = creatorRankingService;
        this.rankingMaintenanceStore = rankingMaintenanceStore;
        this.lockExecutor = lockExecutor;

        this.postSnapshotTimer = Timer.builder("ranking.snapshot.duration")
                .tag("type", "post").register(meterRegistry);
        this.creatorSnapshotTimer = Timer.builder("ranking.snapshot.duration")
                .tag("type", "creator").register(meterRegistry);
        this.topicSnapshotTimer = Timer.builder("ranking.snapshot.duration")
                .tag("type", "topic").register(meterRegistry);
    }

    /**
     * 定时清理过期排行数据并淘汰总榜低分成员（默认每小时）
     *
     * <p>该任务只做清理/淘汰操作，排行榜数据更新依赖事件驱动（缓冲区刷写）。</p>
     */
    @Scheduled(cron = "${ranking.scheduler.hot-posts-cron:0 0 * * * ?}")
    public void cleanupAndTrimHotPosts() {
        lockExecutor.executeWithLock(RankingRedisKeys.schedulerLock("cleanup-hot-posts"), () ->
            postSnapshotTimer.record(() -> {
                try {
                    rankingMaintenanceStore.cleanupExpiredHotPostRankings(LocalDate.now());
                    rankingMaintenanceStore.trimTotalBoards(TOTAL_BOARD_MAX_SIZE);
                    log.info("热榜清理与淘汰任务完成");
                } catch (Exception e) {
                    log.error("热榜清理与淘汰任务失败", e);
                }
            })
        );
    }

    @Scheduled(cron = "${ranking.scheduler.creator-cron:0 30 2 * * ?}")
    public void refreshCreatorRanking() {
        lockExecutor.executeWithLock(RankingRedisKeys.schedulerLock("refresh-creator"), () ->
            creatorSnapshotTimer.record(() -> {
                try {
                    rankingMaintenanceStore.cleanupExpiredCreatorDailyRankings(LocalDate.now());
                    log.info("创作者排行刷新任务完成");
                } catch (Exception e) {
                    log.error("创作者排行刷新失败", e);
                }
            })
        );
    }

    @Scheduled(cron = "${ranking.scheduler.topic-cron:0 0 3 * * ?}")
    public void refreshTopicRanking() {
        lockExecutor.executeWithLock(RankingRedisKeys.schedulerLock("refresh-topic"), () ->
            topicSnapshotTimer.record(() -> {
                try {
                    rankingMaintenanceStore.cleanupExpiredTopicDailyRankings(LocalDate.now());
                    log.info("话题排行刷新任务完成");
                } catch (Exception e) {
                    log.error("话题排行刷新失败", e);
                }
            })
        );
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
