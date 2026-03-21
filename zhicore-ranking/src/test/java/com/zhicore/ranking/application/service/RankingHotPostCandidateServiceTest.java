package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.HotPostCandidateMeta;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.config.RankingCandidateProperties;
import com.zhicore.ranking.infrastructure.redis.RankingRedisKeys;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingHotPostCandidateService Tests")
class RankingHotPostCandidateServiceTest {

    @Mock
    private RankingRedisRepository rankingRedisRepository;

    @Test
    @DisplayName("刷新候选集应从总榜截取前 N 条并写入 meta")
    void refreshCandidatesShouldMaterializeTopEntriesAndMeta() {
        RankingCandidateProperties properties = new RankingCandidateProperties();
        properties.getPosts().setSize(2);
        RankingHotPostCandidateService service =
                new RankingHotPostCandidateService(rankingRedisRepository, properties);
        List<HotScore> source = List.of(
                HotScore.ofWithRank("1001", 98.5D, 1),
                HotScore.ofWithRank("1002", 87.0D, 2)
        );
        when(rankingRedisRepository.getTopRanking(RankingRedisKeys.hotPosts(), 0, 1)).thenReturn(source);
        when(rankingRedisRepository.getSortedSetSize(RankingRedisKeys.hotPosts())).thenReturn(12L);

        service.refreshCandidates();

        ArgumentCaptor<List<HotScore>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<HotPostCandidateMeta> metaCaptor = ArgumentCaptor.forClass(HotPostCandidateMeta.class);
        verify(rankingRedisRepository).replaceHotPostCandidates(entriesCaptor.capture(), metaCaptor.capture());
        assertEquals(2, entriesCaptor.getValue().size());
        assertEquals("1001", entriesCaptor.getValue().get(0).getEntityId());
        HotPostCandidateMeta meta = metaCaptor.getValue();
        assertNotNull(meta.getVersion());
        assertEquals(2, meta.getCandidateSize());
        assertEquals(RankingRedisKeys.hotPosts(), meta.getSourceKey());
        assertEquals(12L, meta.getSourceCount());
        assertEquals(87.0D, meta.getMinScore());
        assertFalse(meta.isStale());
    }

    @Test
    @DisplayName("查询候选集应返回快照并在超时后标记 stale")
    void getCandidatesShouldReturnSnapshotAndMarkStale() {
        RankingCandidateProperties properties = new RankingCandidateProperties();
        properties.getPosts().setStaleThreshold(180_000L);
        RankingHotPostCandidateService service =
                new RankingHotPostCandidateService(rankingRedisRepository, properties);
        HotPostCandidateMeta meta = HotPostCandidateMeta.builder()
                .version("v1")
                .generatedAt(Instant.now().minusSeconds(600))
                .candidateSize(2)
                .sourceKey("ranking:posts:hot")
                .sourceCount(10)
                .minScore(50.0D)
                .stale(false)
                .build();
        when(rankingRedisRepository.getHotPostCandidateMeta()).thenReturn(meta);
        when(rankingRedisRepository.getHotPostCandidates(2)).thenReturn(List.of(
                HotScore.ofWithRank("1001", 98.0D, 1),
                HotScore.ofWithRank("1002", 97.0D, 2)
        ));

        var response = service.getCandidates();

        assertEquals("v1", response.getVersion());
        assertEquals(2, response.getCandidateSize());
        assertTrue(response.isStale());
        assertEquals("1001", response.getItems().get(0).getPostId());
        verify(rankingRedisRepository).updateHotPostCandidateStale(true);
    }

    @Test
    @DisplayName("候选集开关关闭时应跳过刷新")
    void refreshCandidatesShouldSkipWhenDisabled() {
        RankingCandidateProperties properties = new RankingCandidateProperties();
        properties.getPosts().setEnabled(false);
        RankingHotPostCandidateService service =
                new RankingHotPostCandidateService(rankingRedisRepository, properties);

        service.refreshCandidates();

        verify(rankingRedisRepository, never()).replaceHotPostCandidates(anyList(), any(HotPostCandidateMeta.class));
    }
}
