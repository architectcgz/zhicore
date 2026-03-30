package com.zhicore.comment.infrastructure.scheduler;

import com.zhicore.comment.application.service.RankingHotPostCandidateSyncService;
import com.zhicore.common.cache.DistributedLockExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("RankingHotPostCandidateSyncScheduler Tests")
class RankingHotPostCandidateSyncSchedulerTest {

    @Test
    @DisplayName("syncHotPostCandidates should execute under watchdog lock")
    void syncHotPostCandidatesShouldExecuteUnderWatchdogLock() {
        RankingHotPostCandidateSyncService syncService = mock(RankingHotPostCandidateSyncService.class);
        DistributedLockExecutor distributedLockExecutor = mock(DistributedLockExecutor.class);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(distributedLockExecutor).executeWithWatchdogLock(anyString(), any(Runnable.class));

        RankingHotPostCandidateSyncScheduler scheduler = new RankingHotPostCandidateSyncScheduler(syncService, distributedLockExecutor);
        scheduler.syncHotPostCandidates();

        verify(distributedLockExecutor).executeWithWatchdogLock(eq(RankingHotPostCandidateSyncScheduler.HOT_POST_CANDIDATE_SYNC_LOCK_KEY), any(Runnable.class));
        verify(syncService).refreshCandidates();
    }

    @Test
    @DisplayName("syncHotPostCandidates should skip service invocation when lock not acquired")
    void syncHotPostCandidatesShouldSkipServiceInvocationWhenLockNotAcquired() {
        RankingHotPostCandidateSyncService syncService = mock(RankingHotPostCandidateSyncService.class);
        DistributedLockExecutor distributedLockExecutor = mock(DistributedLockExecutor.class);

        RankingHotPostCandidateSyncScheduler scheduler = new RankingHotPostCandidateSyncScheduler(syncService, distributedLockExecutor);
        scheduler.syncHotPostCandidates();

        verify(distributedLockExecutor).executeWithWatchdogLock(eq(RankingHotPostCandidateSyncScheduler.HOT_POST_CANDIDATE_SYNC_LOCK_KEY), any(Runnable.class));
        verify(syncService, never()).refreshCandidates();
    }
}
