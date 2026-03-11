package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.port.store.PostRankingStore;
import com.zhicore.ranking.domain.model.PostStats;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.config.RankingWeightProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostRankingService Tests")
class PostRankingServiceTest {

    @Mock
    private PostRankingStore postRankingStore;

    private HotScoreCalculator hotScoreCalculator;

    @BeforeEach
    void setUp() {
        RankingWeightProperties weightProperties = new RankingWeightProperties();
        weightProperties.setView(1.0);
        weightProperties.setLike(5.0);
        weightProperties.setComment(10.0);
        weightProperties.setFavorite(8.0);
        weightProperties.setHalfLifeDays(7.0);
        hotScoreCalculator = new HotScoreCalculator(weightProperties);
    }

    @Test
    @DisplayName("更新文章热度时应计算分数并委托给 store")
    void updatePostScore_shouldCalculateScoreAndDelegate() {
        PostRankingCommandService service = new PostRankingCommandService(postRankingStore, hotScoreCalculator);
        PostStats stats = PostStats.builder()
                .viewCount(10)
                .likeCount(2)
                .commentCount(1)
                .favoriteCount(1)
                .build();
        LocalDateTime publishedAt = LocalDateTime.now();

        service.updatePostScore("1001", stats, publishedAt);

        verify(postRankingStore).updatePostScore("1001", 38.0);
    }

    @Test
    @DisplayName("获取文章排名时应返回 store 的 one-based 结果")
    void getPostRank_shouldReturnStoreValue() {
        PostRankingQueryService service = new PostRankingQueryService(postRankingStore);
        when(postRankingStore.getPostRank("1001")).thenReturn(3L);

        service.getPostRank("1001");

        verify(postRankingStore).getPostRank("1001");
    }
}
