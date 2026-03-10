package com.zhicore.ranking.application.port.store;

import com.zhicore.ranking.domain.model.HotScore;

import java.util.List;

/**
 * 创作者排行榜存储端口。
 */
public interface CreatorRankingStore {

    void updateCreatorScore(String userId, double score);

    List<String> getHotCreators(int page, int size);

    List<HotScore> getHotCreatorsWithScore(int page, int size);

    Long getCreatorRank(String userId);

    Double getCreatorScore(String userId);

    void removeCreator(String userId);
}
