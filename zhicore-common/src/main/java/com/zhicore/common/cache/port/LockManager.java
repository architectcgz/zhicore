package com.zhicore.common.cache.port;

import java.time.Duration;
import java.util.List;

/**
 * 分布式锁管理器端口接口
 *
 * 定义分布式锁操作的契约，由基础设施层实现（如 Redisson）。
 * 用于防止缓存击穿、并发控制、定时任务互斥等场景。
 *
 * 安全性保证：
 * - 实现必须确保只能释放当前线程持有的锁
 * - 底层锁实现（如 Redisson）提供所有权检查（UUID + ThreadId）
 * - 支持看门狗自动续期（当 leaseTime 为 null 时）
 */
public interface LockManager {

    /**
     * 尝试获取锁（固定过期时间）
     *
     * 注意：指定 leaseTime 后，Redisson 看门狗不会自动续期
     *
     * @param key 锁键（建议格式：{env}:{service}:{category}:{resource}:{action}:lock）
     * @param waitTime 等待时间（null 或 Duration.ZERO 表示不等待）
     * @param leaseTime 锁持有时间（自动释放时间，不能为 null）
     * @return 是否获取成功
     * @throws IllegalArgumentException 如果参数无效
     */
    boolean tryLock(String key, Duration waitTime, Duration leaseTime);

    /**
     * 尝试获取锁（支持公平锁选择）。
     *
     * @param key 锁键
     * @param waitTime 等待时间
     * @param leaseTime 锁持有时间
     * @param fair 是否使用公平锁
     * @return 是否获取成功
     */
    default boolean tryLock(String key, Duration waitTime, Duration leaseTime, boolean fair) {
        return tryLock(key, waitTime, leaseTime);
    }

    /**
     * 尝试获取锁（启用看门狗自动续期）
     *
     * 适用于执行时间不确定的任务，Redisson 会自动续期直到显式释放
     *
     * @param key 锁键
     * @param waitTime 等待时间（null 或 Duration.ZERO 表示不等待）
     * @return 是否获取成功
     * @throws IllegalArgumentException 如果参数无效
     */
    boolean tryLockWithWatchdog(String key, Duration waitTime);

    /**
     * 尝试获取锁（启用看门狗自动续期，支持公平锁选择）。
     *
     * @param key 锁键
     * @param waitTime 等待时间
     * @param fair 是否使用公平锁
     * @return 是否获取成功
     */
    default boolean tryLockWithWatchdog(String key, Duration waitTime, boolean fair) {
        return tryLockWithWatchdog(key, waitTime);
    }

    /**
     * 尝试一次性获取多把锁。
     *
     * 适用于需要同时保护多个共享资源的场景；实现应保证锁顺序一致，
     * 并在部分获取失败时回滚已获取的锁，避免死锁和锁泄漏。
     *
     * @param keys 锁键列表
     * @param waitTime 等待时间
     * @param leaseTime 锁持有时间
     * @return 是否全部获取成功
     */
    default boolean tryLockAll(List<String> keys, Duration waitTime, Duration leaseTime) {
        throw new UnsupportedOperationException("Multi-lock is not supported by this LockManager implementation");
    }

    /**
     * 释放锁
     *
     * 注意：
     * - 只能释放当前线程持有的锁
     * - 如果锁已过期或被其他线程持有，释放操作会被忽略
     * - 建议在 finally 块中调用，确保锁一定会被尝试释放
     *
     * @param key 锁键
     */
    void unlock(String key);

    /**
     * 释放锁（支持公平锁选择）。
     *
     * @param key 锁键
     * @param fair 是否使用公平锁
     */
    default void unlock(String key, boolean fair) {
        unlock(key);
    }

    /**
     * 释放多把锁。
     *
     * @param keys 锁键列表
     */
    default void unlockAll(List<String> keys) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            unlock(key);
        }
    }

    /**
     * 检查锁是否由当前线程持有
     *
     * 用于在长时间运行的任务中检查锁是否仍然有效
     *
     * @param key 锁键
     * @return 是否由当前线程持有
     */
    boolean isHeldByCurrentThread(String key);

    /**
     * 检查锁是否由当前线程持有（支持公平锁选择）。
     *
     * @param key 锁键
     * @param fair 是否使用公平锁
     * @return 是否由当前线程持有
     */
    default boolean isHeldByCurrentThread(String key, boolean fair) {
        return isHeldByCurrentThread(key);
    }

    /**
     * 检查锁当前是否已被任意线程持有。
     *
     * @param key 锁键
     * @return 是否处于加锁状态
     */
    default boolean isLocked(String key) {
        throw new UnsupportedOperationException("Lock state inspection is not supported by this LockManager implementation");
    }
}
