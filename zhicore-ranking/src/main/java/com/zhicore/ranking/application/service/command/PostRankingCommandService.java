package com.zhicore.ranking.application.service.command;

import com.zhicore.ranking.application.port.store.PostRankingStore;
import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 文章排行榜写服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostRankingCommandService {

    private final PostRankingStore postRankingStore;
    private final HotScoreCalculator scoreCalculator;

    public void updatePostScore(String postId, PostStats stats, LocalDateTime publishedAt) {
        double score = scoreCalculator.calculatePostHotScore(stats, publishedAt);
        postRankingStore.updatePostScore(postId, score);
        log.debug("Updated post score: postId={}, score={}", postId, score);
    }

    public void removePost(String postId) {
        postRankingStore.removePost(postId);
        log.info("Removed post from ranking: postId={}", postId);
    }
}
