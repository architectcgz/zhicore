package com.zhicore.ranking.application.service;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.ranking.application.model.RankingArchiveRecord;
import com.zhicore.ranking.application.port.policy.RankingArchivePolicy;
import com.zhicore.ranking.application.port.store.RankingArchiveSourceStore;
import com.zhicore.ranking.application.port.store.RankingArchiveStore;
import com.zhicore.ranking.domain.model.HotScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingArchiveService Tests")
class RankingArchiveServiceTest {

    @Mock
    private RankingArchiveSourceStore rankingArchiveSourceStore;

    @Mock
    private RankingArchiveStore rankingArchiveStore;

    @Mock
    private RankingArchivePolicy rankingArchivePolicy;

    @Mock
    private DistributedLockExecutor distributedLockExecutor;

    private RankingArchiveService rankingArchiveService;

    @BeforeEach
    void setUp() {
        rankingArchiveService = new RankingArchiveService(
                rankingArchiveSourceStore,
                rankingArchiveStore,
                rankingArchivePolicy,
                distributedLockExecutor
        );
    }

    @Test
    @DisplayName("月榜归档应通过 store 读取源数据并写入归档")
    void archiveMonthlyRanking_shouldPersistMonthlyRankings() {
        when(rankingArchivePolicy.monthlyArchiveLockKey()).thenReturn("ranking:archive:monthly");
        when(rankingArchivePolicy.archiveLimit()).thenReturn(100);
        when(rankingArchiveSourceStore.getMonthlyPostRanking(any(Integer.class), any(Integer.class), any(Integer.class)))
                .thenReturn(List.of(HotScore.ofWithRank("1001", 98.0, 1)));
        when(rankingArchiveSourceStore.getMonthlyCreatorRanking(100))
                .thenReturn(List.of(HotScore.ofWithRank("2001", 66.0, 1)));
        when(rankingArchiveSourceStore.getMonthlyTopicRanking(100))
                .thenReturn(List.of(HotScore.ofWithRank("3001", 55.0, 1)));
        when(rankingArchiveStore.saveIfAbsent(any(RankingArchiveRecord.class))).thenReturn(true);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(distributedLockExecutor).executeWithLock(any(String.class), any(Runnable.class));

        rankingArchiveService.archiveMonthlyRanking();

        verify(distributedLockExecutor).executeWithLock(any(String.class), any(Runnable.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RankingArchiveRecord> captor = ArgumentCaptor.forClass(RankingArchiveRecord.class);
        verify(rankingArchiveStore, times(3)).saveIfAbsent(captor.capture());
        assertEquals(List.of("post", "creator", "topic"), captor.getAllValues().stream().map(RankingArchiveRecord::getEntityType).toList());
        assertEquals(List.of("monthly", "monthly", "monthly"), captor.getAllValues().stream().map(RankingArchiveRecord::getRankingType).toList());
    }

    @Test
    @DisplayName("查询月归档应直接委托给 archive store")
    void getMonthlyArchive_shouldDelegateToStore() {
        List<HotScore> archives = List.of(HotScore.ofWithRank("1001", 88.0, 1));
        when(rankingArchiveStore.getMonthlyArchive("post", 2026, 2, 10)).thenReturn(archives);

        List<HotScore> result = rankingArchiveService.getMonthlyArchive("post", 2026, 2, 10);

        assertEquals(archives, result);
        verify(rankingArchiveStore).getMonthlyArchive("post", 2026, 2, 10);
    }
}
