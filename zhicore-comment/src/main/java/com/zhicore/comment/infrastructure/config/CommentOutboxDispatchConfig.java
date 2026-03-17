package com.zhicore.comment.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;

/**
 * 评论服务 outbox worker 线程池配置。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CommentOutboxDispatchConfig {

    private final CommentOutboxProperties properties;

    @Bean(name = "commentOutboxExecutor")
    public ThreadPoolTaskExecutor commentOutboxExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkerCount());
        executor.setMaxPoolSize(properties.getWorkerCount());
        executor.setQueueCapacity(properties.getWorkerCount());
        executor.setThreadNamePrefix("comment-outbox-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(rejectedHandler());
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler rejectedHandler() {
        return (task, executor) -> log.error(
                "Comment outbox 线程池队列已满，worker 提交被拒绝: poolSize={}, active={}, queueSize={}",
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue() != null ? executor.getQueue().size() : -1
        );
    }
}
