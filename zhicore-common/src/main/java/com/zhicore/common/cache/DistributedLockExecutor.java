package com.zhicore.common.cache;

import com.zhicore.common.cache.port.LockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 分布式锁执行器
 *
 * <p>封装分布式锁的常见使用模式，供定时任务、归档任务等场景复用。</p>
 * <p>默认非阻塞模式（waitTime=0），拿不到锁立即跳过，避免多实例重复执行。</p>
 * <p>通过 {@link LockManager} 端口解耦，不直接依赖 Redisson。</p>
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockExecutor {

    private final LockManager lockManager;

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
     * 非阻塞方式执行带看门狗续期的分布式锁任务：拿不到锁立即跳过。
     *
     * <p>适用于执行时长不可预估的任务，避免固定 TTL 锁在执行中途过期。</p>
     *
     * @param lockKey 完整的锁 key
     * @param task 要执行的任务
     */
    public void executeWithWatchdogLock(String lockKey, Runnable task) {
        executeWithWatchdogLock(lockKey, Duration.ZERO, task);
    }

    /**
     * 使用看门狗分布式锁执行任务。
     *
     * @param lockKey 完整的锁 key
     * @param waitTime 等待时间（Duration.ZERO 表示非阻塞）
     * @param task 要执行的任务
     */
    public void executeWithWatchdogLock(String lockKey, Duration waitTime, Runnable task) {
        if (lockManager.tryLockWithWatchdog(lockKey, waitTime)) {
            try {
                log.info("获取看门狗分布式锁成功，开始执行任务: {}", lockKey);
                task.run();
            } finally {
                lockManager.unlock(lockKey);
            }
        } else {
            log.debug("任务已被其他实例执行（看门狗锁），跳过: {}", lockKey);
        }
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
        if (lockManager.tryLock(lockKey, waitTime, leaseTime)) {
            try {
                log.info("获取分布式锁成功，开始执行任务: {}", lockKey);
                task.run();
            } finally {
                lockManager.unlock(lockKey);
            }
        } else {
            log.debug("任务已被其他实例执行，跳过: {}", lockKey);
        }
    }
}
