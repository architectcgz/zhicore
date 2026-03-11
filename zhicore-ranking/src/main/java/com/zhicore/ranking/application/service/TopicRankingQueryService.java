package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.port.store.TopicRankingStore;
import com.zhicore.ranking.domain.model.HotScore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 话题排行榜查询服务。
 */
@Service
@RequiredArgsConstructor
public class TopicRankingQueryService {

    private final TopicRankingStore topicRankingStore;

    public List<Long> getHotTopics(int page, int size) {
        return topicRankingStore.getHotTopics(page, size);
    }

    public List<HotScore> getHotTopicsWithScore(int page, int size) {
        return topicRankingStore.getHotTopicsWithScore(page, size);
    }

    public Long getTopicRank(Long topicId) {
        return topicRankingStore.getTopicRank(topicId);
    }

    public Double getTopicScore(Long topicId) {
        return topicRankingStore.getTopicScore(topicId);
    }
}
