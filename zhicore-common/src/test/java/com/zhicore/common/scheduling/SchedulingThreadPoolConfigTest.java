package com.zhicore.common.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulingThreadPoolConfig Tests")
class SchedulingThreadPoolConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingThreadPoolConfig.class);

    @Test
    @DisplayName("should create custom taskScheduler with configured pool size and thread name prefix")
    void shouldCreateConfiguredTaskScheduler() throws Exception {
        contextRunner
                .withPropertyValues(
                        "app.scheduling.pool-size=3",
                        "app.scheduling.thread-name-prefix=test-scheduler-",
                        "app.scheduling.wait-for-tasks-to-complete-on-shutdown=true",
                        "app.scheduling.await-termination-seconds=15"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskScheduler.class);
                    assertThat(context).hasBean("taskScheduler");

                    ThreadPoolTaskScheduler scheduler = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(3);
                    assertThat(scheduler.getScheduledThreadPoolExecutor().getRemoveOnCancelPolicy()).isTrue();

                    CountDownLatch latch = new CountDownLatch(1);
                    AtomicReference<String> threadName = new AtomicReference<>();
                    scheduler.execute(() -> {
                        threadName.set(Thread.currentThread().getName());
                        latch.countDown();
                    });

                    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
                    assertThat(threadName.get()).startsWith("test-scheduler-");
                });
    }
}
