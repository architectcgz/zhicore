package com.zhicore.common.dispatcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 claim 的可靠任务派发器基类。
 *
 * <p>设计目标：
 * 1. 事务提交后主动唤醒，定时任务只负责兜底补扫
 * 2. 通过数据库 claim + worker 并发替代全局锁单活模型
 * 3. 单个 worker 顺序处理自己 claim 到的任务，异常任务交给子类落失败状态
 */
@Slf4j
public abstract class ClaimBasedDispatcher<T> implements SchedulingConfigurer {

    private final TaskExecutor taskExecutor;
    private final TransactionOperations transactionOperations;
    private final Duration claimTimeout;
    private final AtomicBoolean[] workerRunning;
    private final AtomicBoolean[] wakeRequested;
    private final String[] workerIds;

    protected ClaimBasedDispatcher(String dispatcherName,
                                   int workerCount,
                                   Duration claimTimeout,
                                   TaskExecutor taskExecutor,
                                   TransactionOperations transactionOperations) {
        this.taskExecutor = taskExecutor;
        this.transactionOperations = transactionOperations;
        this.claimTimeout = claimTimeout;
        this.workerRunning = new AtomicBoolean[workerCount];
        this.wakeRequested = new AtomicBoolean[workerCount];
        this.workerIds = new String[workerCount];

        String instanceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        for (int i = 0; i < workerCount; i++) {
            this.workerRunning[i] = new AtomicBoolean(false);
            this.wakeRequested[i] = new AtomicBoolean(false);
            this.workerIds[i] = dispatcherName + "-" + instanceId + "-" + i;
        }
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(this::signal, Duration.ofMillis(sweepIntervalMillis()));
    }

    public final void signal() {
        for (int i = 0; i < workerRunning.length; i++) {
            wakeRequested[i].set(true);
            startWorkerIfIdle(i);
        }
    }

    protected abstract long sweepIntervalMillis();

    protected abstract int batchSize();

    protected abstract List<T> claimBatch(String workerId, int limit, Duration claimTimeout);

    protected abstract void handleClaimed(T task);

    protected abstract void handleFailure(T task, Exception exception);

    private void startWorkerIfIdle(int workerIndex) {
        if (!workerRunning[workerIndex].compareAndSet(false, true)) {
            return;
        }

        try {
            taskExecutor.execute(() -> drainLoop(workerIndex));
        } catch (RejectedExecutionException ex) {
            workerRunning[workerIndex].set(false);
            log.error("派发 worker 提交失败: workerId={}, error={}",
                    workerIds[workerIndex], ex.getMessage(), ex);
        }
    }

    private void drainLoop(int workerIndex) {
        String workerId = workerIds[workerIndex];
        AtomicBoolean workerWakeRequested = wakeRequested[workerIndex];
        try {
            while (true) {
                workerWakeRequested.set(false);
                List<T> claimed = claimBatch(workerId, batchSize(), claimTimeout);
                if (claimed == null || claimed.isEmpty()) {
                    if (workerWakeRequested.get()) {
                        continue;
                    }
                    return;
                }

                for (T task : claimed) {
                    try {
                        transactionOperations.executeWithoutResult(status -> handleClaimed(task));
                    } catch (Exception ex) {
                        transactionOperations.executeWithoutResult(status -> handleFailure(task, ex));
                    }
                }
            }
        } finally {
            workerRunning[workerIndex].set(false);
            if (workerWakeRequested.get()) {
                startWorkerIfIdle(workerIndex);
            }
        }
    }
}
