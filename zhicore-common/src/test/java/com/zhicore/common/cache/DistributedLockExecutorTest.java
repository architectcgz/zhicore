package com.zhicore.common.cache;

import com.zhicore.common.cache.port.LockManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DistributedLockExecutor Tests")
class DistributedLockExecutorTest {

    @Mock
    private LockManager lockManager;

    @Test
    @DisplayName("executeWithWatchdogLock should run task and unlock when lock acquired")
    void executeWithWatchdogLockShouldRunTaskAndUnlockWhenLockAcquired() {
        when(lockManager.tryLockWithWatchdog(eq("test:lock"), eq(Duration.ZERO))).thenReturn(true);
        DistributedLockExecutor executor = new DistributedLockExecutor(lockManager);
        AtomicBoolean executed = new AtomicBoolean(false);

        executor.executeWithWatchdogLock("test:lock", () -> executed.set(true));

        assertThat(executed).isTrue();
        verify(lockManager).unlock("test:lock");
    }

    @Test
    @DisplayName("executeWithWatchdogLock should skip task when lock not acquired")
    void executeWithWatchdogLockShouldSkipTaskWhenLockNotAcquired() {
        when(lockManager.tryLockWithWatchdog(eq("test:lock"), eq(Duration.ZERO))).thenReturn(false);
        DistributedLockExecutor executor = new DistributedLockExecutor(lockManager);
        AtomicBoolean executed = new AtomicBoolean(false);

        executor.executeWithWatchdogLock("test:lock", () -> executed.set(true));

        assertThat(executed).isFalse();
        verify(lockManager, never()).unlock(any());
    }
}
