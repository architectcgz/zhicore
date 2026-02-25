package com.zhicore.ranking.application.service;

import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.mongodb.RankingArchive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 排行榜查询服务（智能路由）
 * 
 * 根据查询日期自动选择数据源：
 * - Redis：TTL 范围内的热数据
 * - MongoDB：TTL 范围外的冷数据
 * 
 * 当前实现：
 * - 月榜路由（必需）
 * - 日榜和周榜路由（可选，未来扩展）
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingQueryService {
    
    private final PostRankingService postRankingService;
    private final RankingArchiveService archiveService;
    
    /**
     * Redis 月榜数据保留天数（TTL）
     */
    private static final int MONTHLY_REDIS_TTL_DAYS = 365;
    
    /**
     * 查询月榜（自动路由到 Redis 或 MongoDB）
     * 
     * 路由逻辑：
     * - 查询日期在 365 天内：从 Redis 查询（热数据）
     * - 查询日期超过 365 天：从 MongoDB 查询（冷数据）
     * 
     * @param year 年份
     * @param month 月份（1-12）
     * @param limit 数量限制
     * @return 热度分数列表（统一返回 HotScore 类型）
     */
    public List<HotScore> getMonthlyRanking(int year, int month, int limit) {
        LocalDate now = LocalDate.now();
        LocalDate queryDate = LocalDate.of(year, month, 1);
        
        // 判断是否在 Redis TTL 范围内（365天）
        boolean inRedisRange = queryDate.isAfter(now.minusDays(MONTHLY_REDIS_TTL_DAYS));
        
        if (inRedisRange) {
            // 从 Redis 查询（热数据）
            log.debug("查询月榜（Redis）: year={}, month={}, limit={}", year, month, limit);
            return postRankingService.getMonthlyHotPostsWithScore(year, month, limit);
        } else {
            // 从 MongoDB 查询（冷数据），转换为 HotScore 格式
            log.debug("查询月榜（MongoDB）: year={}, month={}, limit={}", year, month, limit);
            List<RankingArchive> archives = archiveService.getMonthlyArchive("post", year, month, limit);
            return convertToHotScores(archives);
        }
    }
    
    /**
     * 将 MongoDB 归档数据转换为 HotScore 格式
     * 
     * @param archives 归档数据列表
     * @return HotScore 列表
     */
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
