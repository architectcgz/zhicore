package com.zhicore.ranking.infrastructure.scheduler;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.ranking.application.service.RankingHotPostCandidateService;
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
@DisplayName("RankingHotPostCandidateScheduler Tests")
class RankingHotPostCandidateSchedulerTest {

    @Mock
    private RankingHotPostCandidateService rankingHotPostCandidateService;

    @Mock
    private DistributedLockExecutor distributedLockExecutor;

    private RankingHotPostCandidateScheduler scheduler;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(distributedLockExecutor).executeWithLock(anyString(), any(Runnable.class));

        scheduler = new RankingHotPostCandidateScheduler(
                rankingHotPostCandidateService,
                distributedLockExecutor
        );
    }

    @Test
    @DisplayName("候选集调度器应在锁内触发刷新")
    void refreshShouldDelegateToService() {
        scheduler.refresh();

        verify(rankingHotPostCandidateService).refreshCandidates();
    }
}
