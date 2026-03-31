package com.zhicore.migration.service.gray;

import com.zhicore.common.cache.DistributedLockExecutor;
import com.zhicore.migration.service.gray.store.GrayReleaseStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GrayDataReconciliationTask Tests")
class GrayDataReconciliationTaskTest {

    @Mock
    private GrayReleaseStore grayReleaseStore;

    @Mock
    private DistributedLockExecutor distributedLockExecutor;

    @Test
    @DisplayName("reconcile should execute under watchdog lock and persist result when enabled")
    void reconcileShouldExecuteUnderWatchdogLockAndPersistResultWhenEnabled() {
        GrayReleaseSettings settings = new GrayReleaseSettings(
                true,
                5,
                Set.of(),
                Set.of(),
                new GrayReleaseSettings.AlertSettings(0.01, 500)
        );
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(distributedLockExecutor).executeWithWatchdogLock(anyString(), any(Runnable.class));

        GrayDataReconciliationTask reconciliationTask = new GrayDataReconciliationTask(
                grayReleaseStore,
                settings,
                distributedLockExecutor
        );

        reconciliationTask.reconcile();

        verify(distributedLockExecutor).executeWithWatchdogLock(eq(GrayDataReconciliationTask.RECONCILIATION_LOCK_KEY), any(Runnable.class));
        ArgumentCaptor<GrayDataReconciliationTask.ReconciliationResult> captor = ArgumentCaptor.forClass(GrayDataReconciliationTask.ReconciliationResult.class);
        verify(grayReleaseStore).saveReconciliationResult(captor.capture());
        assertThat(captor.getValue().isSuccess()).isTrue();
        assertThat(captor.getValue().getDetails()).hasSize(4);
    }

    @Test
    @DisplayName("reconcile should return immediately when gray is disabled")
    void reconcileShouldReturnImmediatelyWhenGrayDisabled() {
        GrayReleaseSettings settings = new GrayReleaseSettings(
                false,
                5,
                Set.of(),
                Set.of(),
                new GrayReleaseSettings.AlertSettings(0.01, 500)
        );
        GrayDataReconciliationTask reconciliationTask = new GrayDataReconciliationTask(
                grayReleaseStore,
                settings,
                distributedLockExecutor
        );

        reconciliationTask.reconcile();

        verify(distributedLockExecutor, never()).executeWithWatchdogLock(anyString(), any(Runnable.class));
        verify(grayReleaseStore, never()).saveReconciliationResult(any());
    }
}
