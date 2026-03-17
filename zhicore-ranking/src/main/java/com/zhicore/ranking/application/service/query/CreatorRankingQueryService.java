package com.zhicore.ranking.application.service.query;

import com.zhicore.ranking.application.port.store.CreatorRankingStore;
import com.zhicore.ranking.domain.model.HotScore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 创作者排行榜查询服务。
 */
@Service
@RequiredArgsConstructor
public class CreatorRankingQueryService {

    private final CreatorRankingStore creatorRankingStore;

    public List<String> getHotCreators(int page, int size) {
        return creatorRankingStore.getHotCreators(page, size);
    }

    public List<HotScore> getHotCreatorsWithScore(int page, int size) {
        return creatorRankingStore.getHotCreatorsWithScore(page, size);
    }

    public Long getCreatorRank(String userId) {
        return creatorRankingStore.getCreatorRank(userId);
    }

    public Double getCreatorScore(String userId) {
        return creatorRankingStore.getCreatorScore(userId);
    }
}
