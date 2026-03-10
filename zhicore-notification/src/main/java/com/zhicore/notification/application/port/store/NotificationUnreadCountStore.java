package com.zhicore.notification.application.port.store;

import java.time.Duration;

/**
 * 通知未读数缓存存储端口。
 *
 * 封装未读计数缓存读写与失效逻辑，
 * 避免应用层直接依赖 RedisTemplate。
 */
public interface NotificationUnreadCountStore {

    Integer get(Long userId);

    void set(Long userId, int count, Duration ttl);

    void evict(Long userId);
}
