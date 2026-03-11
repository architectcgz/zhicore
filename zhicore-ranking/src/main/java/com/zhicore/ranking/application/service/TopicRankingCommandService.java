package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.port.store.TopicRankingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 话题排行榜写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicRankingCommandService {

    private final TopicRankingStore topicRankingStore;

    public void updateTopicScore(Long topicId, double score) {
        topicRankingStore.updateTopicScore(topicId, score);
        log.debug("Updated topic score: topicId={}, score={}", topicId, score);
    }

    public void incrementTopicScore(Long topicId, double delta) {
        topicRankingStore.incrementTopicScore(topicId, delta);
        log.debug("Incremented topic score: topicId={}, delta={}", topicId, delta);
    }

    public void removeTopic(Long topicId) {
        topicRankingStore.removeTopic(topicId);
        log.info("Removed topic from ranking: topicId={}", topicId);
    }
}
