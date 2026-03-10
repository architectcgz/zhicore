package com.zhicore.content.application.port.cachekey;

/**
 * 调度任务分布式锁 key 解析端口。
 */
public interface SchedulerLockKeyResolver {

    String consumedEventCleanup();

    String incompletePostCleanup();
}
