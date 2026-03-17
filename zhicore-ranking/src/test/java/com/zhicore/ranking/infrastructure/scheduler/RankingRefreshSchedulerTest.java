package com.zhicore.ranking.infrastructure.scheduler;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.ranking.application.port.store.RankingMaintenanceStore;
import com.zhicore.ranking.application.service.command.CreatorRankingCommandService;
import com.zhicore.ranking.application.service.command.PostRankingCommandService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingRefreshScheduler Tests")
class RankingRefreshSchedulerTest {

    @Mock
    private PostRankingCommandService postRankingService;

    @Mock
    private CreatorRankingCommandService creatorRankingService;

    @Mock
    private RankingMaintenanceStore rankingMaintenanceStore;

    @Mock
    private DistributedLockExecutor distributedLockExecutor;

    private RankingRefreshScheduler rankingRefreshScheduler;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(distributedLockExecutor).executeWithLock(anyString(), any(Runnable.class));

        rankingRefreshScheduler = new RankingRefreshScheduler(
                postRankingService,
                creatorRankingService,
                rankingMaintenanceStore,
                distributedLockExecutor,
                new SimpleMeterRegistry()
        );
    }

    @Test
    @DisplayName("热榜维护调度应委托 maintenance store")
    void cleanupAndTrimHotPosts_shouldDelegateToMaintenanceStore() {
        rankingRefreshScheduler.cleanupAndTrimHotPosts();

        verify(rankingMaintenanceStore).cleanupExpiredHotPostRankings(any());
        verify(rankingMaintenanceStore).trimTotalBoards(10000);
    }

    @Test
    @DisplayName("创作者排行调度应委托 maintenance store")
    void refreshCreatorRanking_shouldDelegateToMaintenanceStore() {
        rankingRefreshScheduler.refreshCreatorRanking();

        verify(rankingMaintenanceStore).cleanupExpiredCreatorDailyRankings(any());
    }

    @Test
    @DisplayName("话题排行调度应委托 maintenance store")
    void refreshTopicRanking_shouldDelegateToMaintenanceStore() {
        rankingRefreshScheduler.refreshTopicRanking();

        verify(rankingMaintenanceStore).cleanupExpiredTopicDailyRankings(any());
    }
}
