package com.zhicore.ranking.application.service;

import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.mongodb.RankingArchive;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
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

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    private static final int MONTHLY_REDIS_TTL_DAYS = 365;

    public RankingQueryService(PostRankingService postRankingService,
                               RankingArchiveService archiveService,
                               MeterRegistry meterRegistry) {
        this.postRankingService = postRankingService;
        this.archiveService = archiveService;

        this.cacheHitCounter = Counter.builder("ranking.cache.hit.total")
                .tag("type", "monthly")
                .description("排行榜缓存命中次数")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("ranking.cache.miss.total")
                .tag("type", "monthly")
                .description("排行榜缓存未命中次数")
                .register(meterRegistry);
    }

    /**
     * 查询月榜（自动路由到 Redis 或 MongoDB）
     */
    public List<HotScore> getMonthlyRanking(int year, int month, int limit) {
        LocalDate now = LocalDate.now();
        LocalDate queryDate = LocalDate.of(year, month, 1);
        boolean inRedisRange = queryDate.isAfter(now.minusDays(MONTHLY_REDIS_TTL_DAYS));

        if (inRedisRange) {
            log.debug("查询月榜（Redis）: year={}, month={}, limit={}", year, month, limit);
            cacheHitCounter.increment();
            return postRankingService.getMonthlyHotPostsWithScore(year, month, limit);
        } else {
            log.debug("查询月榜（MongoDB）: year={}, month={}, limit={}", year, month, limit);
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
}
