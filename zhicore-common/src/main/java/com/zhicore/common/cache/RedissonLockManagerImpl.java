package com.zhicore.common.cache;

import com.zhicore.common.cache.port.LockManager;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁管理器实现
 *
 * 安全性保证：
 * 1. Redisson 所有权检查：使用 UUID + ThreadId 标识锁持有者
 * 2. isHeldByCurrentThread()：释放前验证所有权
 * 3. 原子性操作：底层使用 Lua 脚本保证原子性
 *
 * 看门狗机制：
 * - tryLock(key, waitTime, leaseTime)：固定过期时间，无看门狗
 * - tryLockWithWatchdog(key, waitTime)：启用看门狗自动续期
 */
@Slf4j
@Component
public class RedissonLockManagerImpl implements LockManager {

    private final RedissonClient redissonClient;
    private final Counter lockReleaseFailureCounter;

    public RedissonLockManagerImpl(RedissonClient redissonClient, MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.lockReleaseFailureCounter = Counter.builder("cache.lock.release.failure")
                .description("分布式锁释放失败次数")
                .tag("component", "redisson")
                .register(meterRegistry);
    }

    @Override
    public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
        validateKey(key);
        Duration validatedWaitTime = validateDuration(waitTime, "waitTime");
        Duration validatedLeaseTime = validateLeaseTime(leaseTime);

        try {
            RLock lock = redissonClient.getLock(key);

            if (lock.isHeldByCurrentThread()) {
                log.debug("Lock already held by current thread, skip re-acquire: key={}", key);
                return false;
            }

            boolean acquired = lock.tryLock(
                validatedWaitTime.toMillis(),
                validatedLeaseTime.toMillis(),
                TimeUnit.MILLISECONDS
            );

            if (acquired) {
                log.debug("Lock acquired (fixed TTL): key={}, waitTime={}, leaseTime={}",
                    key, validatedWaitTime, validatedLeaseTime);
            } else {
                log.debug("Failed to acquire lock: key={}, waitTime={}", key, validatedWaitTime);
            }

            return acquired;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted: key={}", key, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to acquire lock: key={}", key, e);
            return false;
        }
    }

    @Override
    public boolean tryLockWithWatchdog(String key, Duration waitTime) {
        validateKey(key);
        Duration validatedWaitTime = validateDuration(waitTime, "waitTime");

        try {
            RLock lock = redissonClient.getLock(key);

            if (lock.isHeldByCurrentThread()) {
                log.debug("Lock already held by current thread, skip re-acquire: key={}", key);
                return false;
            }

            boolean acquired = lock.tryLock(
                validatedWaitTime.toMillis(),
                TimeUnit.MILLISECONDS
            );

            if (acquired) {
                log.debug("Lock acquired (with watchdog): key={}, waitTime={}",
                    key, validatedWaitTime);
            } else {
                log.debug("Failed to acquire lock: key={}, waitTime={}", key, validatedWaitTime);
            }

            return acquired;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted: key={}", key, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to acquire lock: key={}", key, e);
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        validateKey(key);

        try {
            RLock lock = redissonClient.getLock(key);

            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: key={}", key);
            } else {
                log.warn("Attempting to unlock a lock not held by current thread: key={}", key);
            }

        } catch (IllegalMonitorStateException e) {
            log.warn("Attempting to unlock a lock not owned by current thread: key={}", key);
        } catch (Exception e) {
            log.error("Failed to release lock: key={}, error={}", key, e.getMessage(), e);
            lockReleaseFailureCounter.increment();
        }
    }

    @Override
    public boolean isHeldByCurrentThread(String key) {
        validateKey(key);

        try {
            RLock lock = redissonClient.getLock(key);
            return lock.isHeldByCurrentThread();
        } catch (Exception e) {
            log.error("Failed to check lock ownership: key={}", key, e);
            return false;
        }
    }

    private void validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Lock key cannot be null or empty");
        }
        if (!key.contains(":")) {
            log.warn("Lock key should follow format: {{env}}:{{service}}:{{category}}:{{resource}}:{{action}}:lock, got: {}", key);
        }
    }

    private Duration validateDuration(Duration duration, String paramName) {
        if (duration == null) {
            return Duration.ZERO;
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException(paramName + " cannot be negative: " + duration);
        }
        if ("waitTime".equals(paramName) && duration.toMinutes() > 60) {
            log.warn("Unusually large waitTime: {}, consider reducing it", duration);
        }
        return duration;
    }

    private Duration validateLeaseTime(Duration leaseTime) {
        if (leaseTime == null) {
            throw new IllegalArgumentException("leaseTime cannot be null, use tryLockWithWatchdog() for auto-renewal");
        }
        if (leaseTime.isNegative() || leaseTime.isZero()) {
            throw new IllegalArgumentException("leaseTime must be positive: " + leaseTime);
        }
        if (leaseTime.toSeconds() < 10) {
            log.warn("Very short leaseTime: {}, may cause premature expiration", leaseTime);
        }
        if (leaseTime.toMinutes() > 30) {
            log.warn("Very long leaseTime: {}, may cause long blocking on failure", leaseTime);
        }
        return leaseTime;
    }
}
