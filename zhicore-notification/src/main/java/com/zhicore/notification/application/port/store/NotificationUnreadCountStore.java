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

    /**
     * 缓存命中时原子递增未读数。
     *
     * @return true 表示执行了递增；false 表示缓存未命中
     */
    boolean increment(Long userId);

    /**
     * 缓存命中时原子递减未读数。
     *
     * @return true 表示执行了递减；false 表示缓存未命中
     */
    boolean decrement(Long userId, int delta);

    void evict(Long userId);
}
