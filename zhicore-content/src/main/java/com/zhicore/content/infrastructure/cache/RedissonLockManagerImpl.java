package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.application.port.cache.LockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁管理器实现
 * 
 * 实现 LockManager 端口接口，提供基于 Redisson 的分布式锁能力。
 * 
 * 安全性保证：
 * 1. Redisson 所有权检查：使用 UUID + ThreadId 标识锁持有者
 * 2. isHeldByCurrentThread()：释放前验证所有权
 * 3. 原子性操作：底层使用 Lua 脚本保证原子性
 * 
 * 看门狗机制：
 * - tryLock(key, waitTime, leaseTime)：固定过期时间，无看门狗
 * - tryLockWithWatchdog(key, waitTime)：启用看门狗自动续期
 * 
 * 注意：不使用 ThreadLocal，直接依赖 Redisson 的所有权检查
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockManagerImpl implements LockManager {
    
    private final RedissonClient redissonClient;
    
    @Override
    public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
        // 参数校验
        validateKey(key);
        Duration validatedWaitTime = validateDuration(waitTime, "waitTime");
        Duration validatedLeaseTime = validateLeaseTime(leaseTime);
        
        try {
            RLock lock = redissonClient.getLock(key);
            
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
        // 参数校验
        validateKey(key);
        Duration validatedWaitTime = validateDuration(waitTime, "waitTime");
        
        try {
            RLock lock = redissonClient.getLock(key);
            
            // 不指定 leaseTime，启用看门狗自动续期
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
            
            // 检查锁是否由当前线程持有
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: key={}", key);
            } else {
                // 锁已过期或被其他线程持有
                log.warn("Attempting to unlock a lock not held by current thread: key={}", key);
            }
            
        } catch (IllegalMonitorStateException e) {
            // Redisson 抛出此异常表示锁不属于当前线程
            log.warn("Attempting to unlock a lock not owned by current thread: key={}", key);
        } catch (Exception e) {
            // 记录错误并增加监控指标
            log.error("Failed to release lock: key={}, error={}", key, e.getMessage(), e);
            // TODO: 增加监控指标 lockReleaseFailureCounter.increment()
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
    
    /**
     * 校验锁键
     */
    private void validateKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Lock key cannot be null or empty");
        }
        
        // 建议检查 key 格式（可选）
        if (!key.contains(":")) {
            log.warn("Lock key should follow format: {{env}}:{{service}}:{{category}}:{{resource}}:{{action}}:lock, got: {}", key);
        }
    }
    
    /**
     * 校验时间参数
     */
    private Duration validateDuration(Duration duration, String paramName) {
        if (duration == null) {
            return Duration.ZERO;
        }
        
        if (duration.isNegative()) {
            throw new IllegalArgumentException(paramName + " cannot be negative: " + duration);
        }
        
        // 防止超大值（例如超过 1 小时的等待时间不合理）
        if ("waitTime".equals(paramName) && duration.toMinutes() > 60) {
            log.warn("Unusually large waitTime: {}, consider reducing it", duration);
        }
        
        return duration;
    }
    
    /**
     * 校验 leaseTime
     */
    private Duration validateLeaseTime(Duration leaseTime) {
        if (leaseTime == null) {
            throw new IllegalArgumentException("leaseTime cannot be null, use tryLockWithWatchdog() for auto-renewal");
        }
        
        if (leaseTime.isNegative() || leaseTime.isZero()) {
            throw new IllegalArgumentException("leaseTime must be positive: " + leaseTime);
        }
        
        // 警告：过短的 leaseTime 容易导致锁过期
        if (leaseTime.toSeconds() < 10) {
            log.warn("Very short leaseTime: {}, may cause premature expiration", leaseTime);
        }
        
        // 警告：过长的 leaseTime 可能导致故障时长时间阻塞
        if (leaseTime.toMinutes() > 30) {
            log.warn("Very long leaseTime: {}, may cause long blocking on failure", leaseTime);
        }
        
        return leaseTime;
    }
}


