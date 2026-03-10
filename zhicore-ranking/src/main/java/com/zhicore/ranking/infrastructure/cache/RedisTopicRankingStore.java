package com.zhicore.ranking.infrastructure.cache;

import com.zhicore.ranking.application.port.store.TopicRankingStore;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Redis 的话题排行榜存储实现。
 */
@Component
@RequiredArgsConstructor
public class RedisTopicRankingStore implements TopicRankingStore {

    private final RankingRedisRepository rankingRepository;

    @Override
    public void updateTopicScore(Long topicId, double score) {
        rankingRepository.updateTopicScore(topicId, score);
    }

    @Override
    public void incrementTopicScore(Long topicId, double delta) {
        rankingRepository.incrementTopicScore(topicId, delta);
    }

    @Override
    public List<Long> getHotTopics(int page, int size) {
        return rankingRepository.getHotTopics(page, size);
    }

    @Override
    public List<HotScore> getHotTopicsWithScore(int page, int size) {
        int start = page * size;
        int end = start + size - 1;
        return rankingRepository.getTopRanking(RankingRedisKeys.hotTopics(), start, end);
    }

    @Override
    public Long getTopicRank(Long topicId) {
        Long rank = rankingRepository.getRank(RankingRedisKeys.hotTopics(), topicId.toString());
        return rank != null ? rank + 1 : null;
    }

    @Override
    public Double getTopicScore(Long topicId) {
        return rankingRepository.getScore(RankingRedisKeys.hotTopics(), topicId.toString());
    }

    @Override
    public void removeTopic(Long topicId) {
        rankingRepository.removeMember(RankingRedisKeys.hotTopics(), topicId.toString());
    }
}
