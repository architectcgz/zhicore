package com.zhicore.message.infrastructure.mq;

import com.zhicore.common.dispatcher.ClaimBasedDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Message ClaimBasedDispatcher 测试")
class ClaimBasedDispatcherTest {

    @Test
    @DisplayName("worker 忙碌期间再次 signal 不应丢失唤醒")
    void shouldNotLoseSignalWhileWorkerIsRunning() throws Exception {
        TestDispatcher dispatcher = new TestDispatcher();

        dispatcher.signal();
        assertTrue(dispatcher.awaitFirstClaim(), "第一次 claim 没有启动");

        dispatcher.makeSecondTaskAvailable();
        dispatcher.signal();
        dispatcher.releaseFirstClaim();

        assertTrue(dispatcher.awaitHandled(), "第二次 signal 后的任务没有被处理");
        assertEquals(List.of("task-2"), dispatcher.handledTasks());
    }

    private static final class TestDispatcher extends ClaimBasedDispatcher<String> {

        private final AtomicInteger claimCount = new AtomicInteger();
        private final AtomicBoolean secondTaskAvailable = new AtomicBoolean(false);
        private final CountDownLatch firstClaimEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirstClaim = new CountDownLatch(1);
        private final CountDownLatch handled = new CountDownLatch(1);
        private final CopyOnWriteArrayList<String> handledTasks = new CopyOnWriteArrayList<>();

        private TestDispatcher() {
            super(
                    "test-dispatcher",
                    1,
                    Duration.ofSeconds(5),
                    asyncExecutor(),
                    immediateTransactions()
            );
        }

        @Override
        protected long sweepIntervalMillis() {
            return 1000L;
        }

        @Override
        protected int batchSize() {
            return 16;
        }

        @Override
        protected List<String> claimBatch(String workerId, int limit, Duration claimTimeout) {
            int current = claimCount.getAndIncrement();
            if (current == 0) {
                firstClaimEntered.countDown();
                awaitLatch(releaseFirstClaim);
                return List.of();
            }
            if (secondTaskAvailable.compareAndSet(true, false)) {
                return List.of("task-2");
            }
            return List.of();
        }

        @Override
        protected void handleClaimed(String task) {
            handledTasks.add(task);
            handled.countDown();
        }

        @Override
        protected void handleFailure(String task, Exception exception) {
            throw new AssertionError("测试不应进入失败路径", exception);
        }

        private void makeSecondTaskAvailable() {
            secondTaskAvailable.set(true);
        }

        private boolean awaitFirstClaim() throws InterruptedException {
            return firstClaimEntered.await(1, TimeUnit.SECONDS);
        }

        private void releaseFirstClaim() {
            releaseFirstClaim.countDown();
        }

        private boolean awaitHandled() throws InterruptedException {
            return handled.await(1, TimeUnit.SECONDS);
        }

        private List<String> handledTasks() {
            return List.copyOf(handledTasks);
        }

        private static TaskExecutor asyncExecutor() {
            return command -> {
                Thread thread = new Thread(command, "message-claim-dispatcher-test");
                thread.setDaemon(true);
                thread.start();
            };
        }

        private static TransactionOperations immediateTransactions() {
            return new TransactionOperations() {
                @Override
                public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                    return action.doInTransaction(mock(TransactionStatus.class));
                }
            };
        }

        private static void awaitLatch(CountDownLatch latch) {
            try {
                if (!latch.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("等待测试同步点超时");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待测试同步点被中断", e);
            }
        }
    }
}
