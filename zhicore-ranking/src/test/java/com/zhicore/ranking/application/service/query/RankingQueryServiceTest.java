package com.zhicore.ranking.application.service.query;

import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.ranking.application.port.policy.RankingQueryPolicy;
import com.zhicore.ranking.application.port.store.RankingArchiveStore;
import com.zhicore.ranking.application.port.store.RankingMonthlyStore;
import com.zhicore.ranking.domain.model.HotScore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingQueryService Tests")
class RankingQueryServiceTest {

    @Mock
    private RankingMonthlyStore rankingMonthlyStore;

    @Mock
    private RankingArchiveStore rankingArchiveStore;

    @Mock
    private LockManager lockManager;

    @Mock
    private RankingQueryPolicy rankingQueryPolicy;

    private RankingQueryService rankingQueryService;
    private Duration lockWaitTime;
    private Duration lockLeaseTime;

    @BeforeEach
    void setUp() {
        lockWaitTime = Duration.ofSeconds(5);
        lockLeaseTime = Duration.ofSeconds(30);
        when(rankingQueryPolicy.isMonthlyQueryInRedisRange(any(), any())).thenReturn(true);

        rankingQueryService = new RankingQueryService(
                rankingMonthlyStore,
                rankingArchiveStore,
                lockManager,
                rankingQueryPolicy,
                new SimpleMeterRegistry()
        );
    }

    @Test
    @DisplayName("命中空结果缓存时应直接返回空列表")
    void getMonthlyRanking_shouldReturnEmptyWhenEmptyMarkerExists() {
        LocalDate now = LocalDate.now();
        when(rankingMonthlyStore.getMonthlyRanking(now.getYear(), now.getMonthValue(), 10))
                .thenReturn(CacheResult.nullValue());

        List<HotScore> result = rankingQueryService.getMonthlyRanking(now.getYear(), now.getMonthValue(), 10);

        assertEquals(Collections.emptyList(), result);
        verify(rankingArchiveStore, never()).getMonthlyArchive(any(), anyInt(), anyInt(), anyInt());
        verify(lockManager, never()).tryLock(any(), any(), any());
    }

    @Test
    @DisplayName("锁获取成功且 Mongo 无数据时应写入空结果缓存")
    void getMonthlyRanking_shouldCacheEmptyMarkerWhenArchiveIsEmpty() {
        LocalDate now = LocalDate.now();
        when(rankingQueryPolicy.monthlyLoadLockWaitTime()).thenReturn(lockWaitTime);
        when(rankingQueryPolicy.monthlyLoadLockLeaseTime()).thenReturn(lockLeaseTime);
        when(rankingQueryPolicy.monthlyBackfillMaxSize()).thenReturn(1000);
        when(rankingMonthlyStore.getMonthlyRanking(now.getYear(), now.getMonthValue(), 10))
                .thenReturn(CacheResult.miss(), CacheResult.miss());
        when(rankingMonthlyStore.monthlyLoadLockKey(now.getYear(), now.getMonthValue()))
                .thenReturn("ranking:monthly:lock");
        when(lockManager.tryLock(
                "ranking:monthly:lock",
                lockWaitTime,
                lockLeaseTime))
                .thenReturn(true);
        when(rankingArchiveStore.getMonthlyArchive("post", now.getYear(), now.getMonthValue(), 1000))
                .thenReturn(Collections.emptyList());

        List<HotScore> result = rankingQueryService.getMonthlyRanking(now.getYear(), now.getMonthValue(), 10);

        assertEquals(Collections.emptyList(), result);
        verify(rankingMonthlyStore).cacheEmptyMonthlyRanking(now.getYear(), now.getMonthValue());
        verify(lockManager).unlock("ranking:monthly:lock");
        verify(rankingMonthlyStore, never()).backfillMonthlyRanking(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("锁获取成功时应回源 Mongo 并回填 Redis")
    void getMonthlyRanking_shouldBackfillRedisWhenArchiveHasData() {
        LocalDate now = LocalDate.now();
        when(rankingQueryPolicy.monthlyLoadLockWaitTime()).thenReturn(lockWaitTime);
        when(rankingQueryPolicy.monthlyLoadLockLeaseTime()).thenReturn(lockLeaseTime);
        when(rankingQueryPolicy.monthlyBackfillMaxSize()).thenReturn(1000);
        when(rankingMonthlyStore.getMonthlyRanking(now.getYear(), now.getMonthValue(), 1))
                .thenReturn(CacheResult.miss(), CacheResult.miss());
        when(rankingMonthlyStore.monthlyLoadLockKey(now.getYear(), now.getMonthValue()))
                .thenReturn("ranking:monthly:lock");
        when(lockManager.tryLock(
                "ranking:monthly:lock",
                lockWaitTime,
                lockLeaseTime))
                .thenReturn(true);

        when(rankingArchiveStore.getMonthlyArchive("post", now.getYear(), now.getMonthValue(), 1000))
                .thenReturn(List.of(
                        HotScore.ofWithRank("1001", 98.0, 1),
                        HotScore.ofWithRank("1002", 86.0, 2)
                ));

        List<HotScore> result = rankingQueryService.getMonthlyRanking(now.getYear(), now.getMonthValue(), 1);

        assertEquals(1, result.size());
        assertEquals("1001", result.get(0).getEntityId());
        assertEquals(98.0, result.get(0).getScore());
        ArgumentCaptor<List<HotScore>> hotScoresCaptor = ArgumentCaptor.forClass(List.class);
        verify(rankingMonthlyStore).backfillMonthlyRanking(
                org.mockito.ArgumentMatchers.eq(now.getYear()),
                org.mockito.ArgumentMatchers.eq(now.getMonthValue()),
                hotScoresCaptor.capture());
        assertEquals(List.of("1001", "1002"), hotScoresCaptor.getValue().stream().map(HotScore::getEntityId).toList());
        assertEquals(List.of(98.0, 86.0), hotScoresCaptor.getValue().stream().map(HotScore::getScore).toList());
        verify(lockManager).unlock("ranking:monthly:lock");
    }

    @Test
    @DisplayName("锁获取失败时应直接回源 Mongo 不回填 Redis")
    void getMonthlyRanking_shouldFallbackToArchiveWhenLockNotAcquired() {
        LocalDate now = LocalDate.now();
        when(rankingQueryPolicy.monthlyLoadLockWaitTime()).thenReturn(lockWaitTime);
        when(rankingQueryPolicy.monthlyLoadLockLeaseTime()).thenReturn(lockLeaseTime);
        when(rankingMonthlyStore.getMonthlyRanking(now.getYear(), now.getMonthValue(), 5))
                .thenReturn(CacheResult.miss(), CacheResult.miss());
        when(rankingMonthlyStore.monthlyLoadLockKey(now.getYear(), now.getMonthValue()))
                .thenReturn("ranking:monthly:lock");
        when(lockManager.tryLock(
                "ranking:monthly:lock",
                lockWaitTime,
                lockLeaseTime))
                .thenReturn(false);
        when(rankingArchiveStore.getMonthlyArchive("post", now.getYear(), now.getMonthValue(), 5))
                .thenReturn(List.of(HotScore.ofWithRank("2001", 64.0, 1)));

        List<HotScore> result = rankingQueryService.getMonthlyRanking(now.getYear(), now.getMonthValue(), 5);

        assertEquals(1, result.size());
        assertEquals("2001", result.get(0).getEntityId());
        verify(lockManager, never()).unlock(any());
        verify(rankingMonthlyStore, never()).backfillMonthlyRanking(anyInt(), anyInt(), any());
        verify(rankingMonthlyStore, never()).cacheEmptyMonthlyRanking(anyInt(), anyInt());
    }
}
