package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.port.store.CreatorRankingStore;
import com.zhicore.ranking.domain.model.CreatorStats;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 创作者排行榜写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorRankingCommandService {

    private final CreatorRankingStore creatorRankingStore;
    private final HotScoreCalculator scoreCalculator;

    public void updateCreatorScore(String userId, CreatorStats stats) {
        double score = scoreCalculator.calculateCreatorHotScore(stats);
        creatorRankingStore.updateCreatorScore(userId, score);
        log.debug("Updated creator score: userId={}, score={}", userId, score);
    }

    public void removeCreator(String userId) {
        creatorRankingStore.removeCreator(userId);
        log.info("Removed creator from ranking: userId={}", userId);
    }
}
