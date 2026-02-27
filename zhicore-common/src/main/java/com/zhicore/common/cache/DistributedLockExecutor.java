package com.zhicore.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁执行器
 *
 * <p>封装 Redisson 分布式锁的常见使用模式，供定时任务、归档任务等场景复用。</p>
 * <p>默认非阻塞模式（waitTime=0），拿不到锁立即跳过，避免多实例重复执行。</p>
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockExecutor {

    private final RedissonClient redissonClient;

    /** 默认锁持有时间 */
    private static final Duration DEFAULT_LEASE_TIME = Duration.ofMinutes(30);

    /**
     * 非阻塞方式执行任务：拿不到锁立即跳过
     *
     * @param lockKey   完整的锁 key
     * @param task      要执行的任务
     */
    public void executeWithLock(String lockKey, Runnable task) {
        executeWithLock(lockKey, Duration.ZERO, DEFAULT_LEASE_TIME, task);
    }

    /**
     * 使用分布式锁执行任务
     *
     * @param lockKey   完整的锁 key
     * @param waitTime  等待时间（Duration.ZERO 表示非阻塞）
     * @param leaseTime 锁持有时间（自动释放）
     * @param task      要执行的任务
     */
    public void executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, Runnable task) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS)) {
                try {
                    log.info("获取分布式锁成功，开始执行任务: {}", lockKey);
                    task.run();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
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
