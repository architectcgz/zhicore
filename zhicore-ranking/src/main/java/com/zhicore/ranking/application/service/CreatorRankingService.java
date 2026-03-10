package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.port.store.CreatorRankingStore;
import com.zhicore.ranking.domain.model.CreatorStats;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 创作者排行榜服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorRankingService {

    private final CreatorRankingStore creatorRankingStore;
    private final HotScoreCalculator scoreCalculator;

    /**
     * 更新创作者热度分数
     *
     * @param userId 用户ID
     * @param stats 创作者统计数据
     */
    public void updateCreatorScore(String userId, CreatorStats stats) {
        double score = scoreCalculator.calculateCreatorHotScore(stats);
        creatorRankingStore.updateCreatorScore(userId, score);
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
        return creatorRankingStore.getHotCreators(page, size);
    }

    /**
     * 获取创作者排行带分数
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 热度分数列表
     */
    public List<HotScore> getHotCreatorsWithScore(int page, int size) {
        return creatorRankingStore.getHotCreatorsWithScore(page, size);
    }

    /**
     * 获取创作者排名
     *
     * @param userId 用户ID
     * @return 排名（从1开始），如果不在排行榜中返回null
     */
    public Long getCreatorRank(String userId) {
        return creatorRankingStore.getCreatorRank(userId);
    }

    /**
     * 获取创作者热度分数
     *
     * @param userId 用户ID
     * @return 热度分数
     */
    public Double getCreatorScore(String userId) {
        return creatorRankingStore.getCreatorScore(userId);
    }

    /**
     * 从排行榜中移除创作者
     *
     * @param userId 用户ID
     */
    public void removeCreator(String userId) {
        creatorRankingStore.removeCreator(userId);
        log.info("Removed creator from ranking: userId={}", userId);
    }
}
