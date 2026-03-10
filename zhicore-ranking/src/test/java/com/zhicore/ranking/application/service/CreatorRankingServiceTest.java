package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.port.store.CreatorRankingStore;
import com.zhicore.ranking.domain.model.CreatorStats;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.config.RankingWeightProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreatorRankingService Tests")
class CreatorRankingServiceTest {

    @Mock
    private CreatorRankingStore creatorRankingStore;

    private HotScoreCalculator hotScoreCalculator;

    @BeforeEach
    void setUp() {
        RankingWeightProperties weightProperties = new RankingWeightProperties();
        weightProperties.setFollower(2.0);
        weightProperties.setCreatorLike(1.0);
        weightProperties.setCreatorComment(1.5);
        weightProperties.setPostCount(3.0);
        hotScoreCalculator = new HotScoreCalculator(weightProperties);
    }

    @Test
    @DisplayName("更新创作者热度时应计算分数并委托给 store")
    void updateCreatorScore_shouldCalculateScoreAndDelegate() {
        CreatorRankingService service = new CreatorRankingService(creatorRankingStore, hotScoreCalculator);
        CreatorStats stats = CreatorStats.builder()
                .followersCount(10)
                .totalLikes(5L)
                .totalComments(4L)
                .postCount(2)
                .build();

        service.updateCreatorScore("2001", stats);

        verify(creatorRankingStore).updateCreatorScore("2001", 37.0);
    }

    @Test
    @DisplayName("获取创作者排名时应直接返回 store 结果")
    void getCreatorRank_shouldReturnStoreValue() {
        CreatorRankingService service = new CreatorRankingService(creatorRankingStore, hotScoreCalculator);
        when(creatorRankingStore.getCreatorRank("2001")).thenReturn(2L);

        service.getCreatorRank("2001");

        verify(creatorRankingStore).getCreatorRank("2001");
    }
}
