package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.port.store.TopicRankingStore;
import com.zhicore.ranking.domain.model.HotScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 话题排行榜服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicRankingService {

    private final TopicRankingStore topicRankingStore;

    /**
     * 更新话题热度分数
     *
     * @param topicId 话题ID
     * @param score 热度分数
     */
    public void updateTopicScore(Long topicId, double score) {
        topicRankingStore.updateTopicScore(topicId, score);
        log.debug("Updated topic score: topicId={}, score={}", topicId, score);
    }

    /**
     * 增量更新话题热度分数
     *
     * @param topicId 话题ID
     * @param delta 增量
     */
    public void incrementTopicScore(Long topicId, double delta) {
        topicRankingStore.incrementTopicScore(topicId, delta);
        log.debug("Incremented topic score: topicId={}, delta={}", topicId, delta);
    }

    /**
     * 获取热门话题排行
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 话题ID列表
     */
    public List<Long> getHotTopics(int page, int size) {
        return topicRankingStore.getHotTopics(page, size);
    }

    /**
     * 获取热门话题排行带分数
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 热度分数列表
     */
    public List<HotScore> getHotTopicsWithScore(int page, int size) {
        return topicRankingStore.getHotTopicsWithScore(page, size);
    }

    /**
     * 获取话题排名
     *
     * @param topicId 话题ID
     * @return 排名（从1开始），如果不在排行榜中返回null
     */
    public Long getTopicRank(Long topicId) {
        return topicRankingStore.getTopicRank(topicId);
    }

    /**
     * 获取话题热度分数
     *
     * @param topicId 话题ID
     * @return 热度分数
     */
    public Double getTopicScore(Long topicId) {
        return topicRankingStore.getTopicScore(topicId);
    }

    /**
     * 从排行榜中移除话题
     *
     * @param topicId 话题ID
     */
    public void removeTopic(Long topicId) {
        topicRankingStore.removeTopic(topicId);
        log.info("Removed topic from ranking: topicId={}", topicId);
    }
}
