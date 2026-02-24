package com.zhicore.content.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步事件线程池配置（R15）
 *
 * 约束：
 * - 线程名前缀固定为 async-event-（可配置）
 * - 拒绝策略不使用 CallerRuns，避免关键链路被拉长；默认“丢弃 + 告警日志”
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AsyncEventConfig implements AsyncConfigurer {

    private final AsyncEventProperties properties;

    @Bean(name = "asyncEventExecutor")
    public ThreadPoolTaskExecutor asyncEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(false);

        executor.setRejectedExecutionHandler(rejectedHandler());
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler rejectedHandler() {
        return (r, e) -> {
            // 注意：这里不走 CallerRuns，避免占用业务线程；默认丢弃并输出错误日志便于观测
            int queueSize = e.getQueue() != null ? e.getQueue().size() : -1;
            log.error("异步事件线程池队列已满，任务被拒绝并丢弃: poolSize={}, active={}, queueSize={}, queueCapacity={}",
                    e.getPoolSize(),
                    e.getActiveCount(),
                    queueSize,
                    properties.getQueueCapacity());
        };
    }

    @Override
    public Executor getAsyncExecutor() {
        return asyncEventExecutor();
    }

    @Override
    public org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            log.error("异步方法未捕获异常: method={}, error={}", method != null ? method.getName() : "unknown", ex.getMessage(), ex);
        };
    }
}

