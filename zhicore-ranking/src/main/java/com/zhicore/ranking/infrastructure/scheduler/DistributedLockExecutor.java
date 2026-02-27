package com.zhicore.ranking.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁执行器
 *
 * <p>封装 Redisson tryLock(0, 30min) 非阻塞模式，供定时任务和归档任务复用。
 * 拿不到锁立即跳过，避免多实例重复执行。</p>
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockExecutor {

    private final RedissonClient redissonClient;

    /**
     * 使用分布式锁执行任务
     *
     * @param lockKey  完整的锁 key
     * @param task     要执行的任务
     */
    public void executeWithLock(String lockKey, Runnable task) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(0, 30, TimeUnit.MINUTES)) {
                try {
                    log.info("获取分布式锁成功，开始执行任务: {}", lockKey);
                    task.run();
                } finally {
                    lock.unlock();
                }
            } else {
                log.debug("任务已被其他实例执行，跳过: {}", lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取分布式锁被中断: {}", lockKey);
        }
    }
}
