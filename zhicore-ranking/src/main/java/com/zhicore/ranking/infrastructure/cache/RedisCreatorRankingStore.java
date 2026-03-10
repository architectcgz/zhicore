package com.zhicore.ranking.infrastructure.cache;

import com.zhicore.ranking.application.port.store.CreatorRankingStore;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Redis 的创作者排行榜存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisCreatorRankingStore implements CreatorRankingStore {

    private final RankingRedisRepository rankingRepository;

    @Override
    public void updateCreatorScore(String userId, double score) {
        rankingRepository.updateCreatorScore(userId, score);
    }

    @Override
    public List<String> getHotCreators(int page, int size) {
        return rankingRepository.getHotCreators(page, size);
    }

    @Override
    public List<HotScore> getHotCreatorsWithScore(int page, int size) {
        int start = page * size;
        int end = start + size - 1;
        return rankingRepository.getTopRanking(RankingRedisKeys.hotCreators(), start, end);
    }

    @Override
    public Long getCreatorRank(String userId) {
        Long rank = rankingRepository.getRank(RankingRedisKeys.hotCreators(), userId);
        return rank != null ? rank + 1 : null;
    }

    @Override
    public Double getCreatorScore(String userId) {
        return rankingRepository.getScore(RankingRedisKeys.hotCreators(), userId);
    }

    @Override
    public void removeCreator(String userId) {
        rankingRepository.removeMember(RankingRedisKeys.hotCreators(), userId);
    }
}
