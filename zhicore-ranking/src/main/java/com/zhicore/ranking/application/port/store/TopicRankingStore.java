package com.zhicore.ranking.application.port.store;

import com.zhicore.ranking.domain.model.HotScore;

import java.util.List;

/**
 * 话题排行榜存储端口。
 */
public interface TopicRankingStore {

    void updateTopicScore(Long topicId, double score);

    void incrementTopicScore(Long topicId, double delta);

    List<Long> getHotTopics(int page, int size);

    List<HotScore> getHotTopicsWithScore(int page, int size);

    Long getTopicRank(Long topicId);

    Double getTopicScore(Long topicId);

    void removeTopic(Long topicId);
}
