package com.zhicore.common.cache;

import com.zhicore.common.cache.port.LockManager;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
        return tryLock(key, waitTime, leaseTime, false);
    }

    @Override
    public boolean tryLock(String key, Duration waitTime, Duration leaseTime, boolean fair) {
        validateKey(key);
        Duration validatedWaitTime = validateDuration(waitTime, "waitTime");
        Duration validatedLeaseTime = validateLeaseTime(leaseTime);

        try {
            RLock lock = getLock(key, fair);

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
        return tryLockWithWatchdog(key, waitTime, false);
    }

    @Override
    public boolean tryLockWithWatchdog(String key, Duration waitTime, boolean fair) {
        validateKey(key);
        Duration validatedWaitTime = validateDuration(waitTime, "waitTime");

        try {
            RLock lock = getLock(key, fair);

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
    public boolean tryLockAll(List<String> keys, Duration waitTime, Duration leaseTime) {
        if (keys == null || keys.isEmpty()) {
            return true;
        }

        Duration validatedWaitTime = validateDuration(waitTime, "waitTime");
        Duration validatedLeaseTime = validateLeaseTime(leaseTime);
        List<String> normalizedKeys = normalizeKeys(keys);

        try {
            List<RLock> locks = normalizedKeys.stream()
                    .map(redissonClient::getLock)
                    .toList();
            if (locks.stream().anyMatch(RLock::isHeldByCurrentThread)) {
                log.debug("One of the locks is already held by current thread, skip re-acquire: keys={}", normalizedKeys);
                return false;
            }

            RedissonMultiLock multiLock = new RedissonMultiLock(locks.toArray(new RLock[0]));
            boolean acquired = multiLock.tryLock(
                    validatedWaitTime.toMillis(),
                    validatedLeaseTime.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            if (acquired) {
                log.debug("Multi lock acquired: keys={}, waitTime={}, leaseTime={}",
                        normalizedKeys, validatedWaitTime, validatedLeaseTime);
            } else {
                log.debug("Failed to acquire multi lock: keys={}, waitTime={}", normalizedKeys, validatedWaitTime);
            }

            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Multi lock acquisition interrupted: keys={}", normalizedKeys, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to acquire multi lock: keys={}", normalizedKeys, e);
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        unlock(key, false);
    }

    @Override
    public void unlock(String key, boolean fair) {
        validateKey(key);

        try {
            RLock lock = getLock(key, fair);

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
    public void unlockAll(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> normalizedKeys = normalizeKeys(keys);
        for (int i = normalizedKeys.size() - 1; i >= 0; i--) {
            unlock(normalizedKeys.get(i));
        }
    }

    @Override
    public boolean isHeldByCurrentThread(String key) {
        return isHeldByCurrentThread(key, false);
    }

    @Override
    public boolean isHeldByCurrentThread(String key, boolean fair) {
        validateKey(key);

        try {
            RLock lock = getLock(key, fair);
            return lock.isHeldByCurrentThread();
        } catch (Exception e) {
            log.error("Failed to check lock ownership: key={}", key, e);
            return false;
        }
    }

    @Override
    public boolean isLocked(String key) {
        validateKey(key);

        try {
            return redissonClient.getLock(key).isLocked();
        } catch (Exception e) {
            log.error("Failed to inspect lock state: key={}", key, e);
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

    private RLock getLock(String key, boolean fair) {
        return fair ? redissonClient.getFairLock(key) : redissonClient.getLock(key);
    }

    private List<String> normalizeKeys(List<String> keys) {
        List<String> normalizedKeys = new ArrayList<>();
        for (String key : keys) {
            validateKey(key);
            normalizedKeys.add(key);
        }
        return normalizedKeys.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
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
