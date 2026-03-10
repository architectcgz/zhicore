package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.application.port.cachekey.SchedulerLockKeyResolver;
import org.springframework.stereotype.Component;

/**
 * 默认调度任务分布式锁 key 解析实现。
 */
@Component
public class DefaultSchedulerLockKeyResolver implements SchedulerLockKeyResolver {

    private final LockKeys lockKeys;

    public DefaultSchedulerLockKeyResolver(LockKeys lockKeys) {
        this.lockKeys = lockKeys;
    }

    @Override
    public String consumedEventCleanup() {
        return lockKeys.consumedEventCleanup();
    }

    @Override
    public String incompletePostCleanup() {
        return lockKeys.incompletePostCleanup();
    }
}
