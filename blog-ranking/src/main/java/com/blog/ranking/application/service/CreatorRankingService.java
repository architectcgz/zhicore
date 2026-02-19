package com.blog.ranking.application.service;

import com.blog.ranking.domain.model.CreatorStats;
import com.blog.ranking.domain.model.HotScore;
import com.blog.ranking.domain.service.HotScoreCalculator;
import com.blog.ranking.infrastructure.redis.RankingRedisKeys;
import com.blog.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 创作者排行榜服务
 *
 * @author Blog Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorRankingService {

    private final RankingRedisRepository rankingRepository;
    private final HotScoreCalculator scoreCalculator;

    /**
     * 更新创作者热度分数
     *
     * @param userId 用户ID
     * @param stats 创作者统计数据
     */
    public void updateCreatorScore(String userId, CreatorStats stats) {
        double score = scoreCalculator.calculateCreatorHotScore(stats);
        rankingRepository.updateCreatorScore(userId, score);
        log.debug("Updated creator score: userId={}, score={}", userId, score);
    }

    /**
     * 获取创作者排行
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 用户ID列表
     */
    public List<String> getHotCreators(int page, int size) {
        return rankingRepository.getHotCreators(page, size);
    }

    /**
     * 获取创作者排行带分数
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 热度分数列表
     */
    public List<HotScore> getHotCreatorsWithScore(int page, int size) {
        int start = page * size;
        int end = start + size - 1;
        return rankingRepository.getTopRanking(RankingRedisKeys.hotCreators(), start, end);
    }

    /**
     * 获取创作者排名
     *
     * @param userId 用户ID
     * @return 排名（从1开始），如果不在排行榜中返回null
     */
    public Long getCreatorRank(String userId) {
        Long rank = rankingRepository.getRank(RankingRedisKeys.hotCreators(), userId);
        return rank != null ? rank + 1 : null;
    }

    /**
     * 获取创作者热度分数
     *
     * @param userId 用户ID
     * @return 热度分数
     */
    public Double getCreatorScore(String userId) {
        return rankingRepository.getScore(RankingRedisKeys.hotCreators(), userId);
    }

    /**
     * 从排行榜中移除创作者
     *
     * @param userId 用户ID
     */
    public void removeCreator(String userId) {
        rankingRepository.removeMember(RankingRedisKeys.hotCreators(), userId);
        log.info("Removed creator from ranking: userId={}", userId);
    }
}
