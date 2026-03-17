package com.zhicore.user.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;

/**
 * 用户服务 outbox worker 线程池配置。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UserOutboxDispatchConfig {

    private final UserOutboxProperties properties;

    @Bean(name = "userOutboxExecutor")
    public ThreadPoolTaskExecutor userOutboxExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkerCount());
        executor.setMaxPoolSize(properties.getWorkerCount());
        executor.setQueueCapacity(properties.getWorkerCount());
        executor.setThreadNamePrefix("user-outbox-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(rejectedHandler());
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler rejectedHandler() {
        return (task, executor) -> log.error(
                "User outbox 线程池队列已满，worker 提交被拒绝: poolSize={}, active={}, queueSize={}",
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue() != null ? executor.getQueue().size() : -1
        );
    }
}
