package com.zhicore.notification.application.port.store;

import com.zhicore.common.result.PageResult;
import com.zhicore.notification.application.dto.AggregatedNotificationVO;

import java.time.Duration;

/**
 * 通知聚合结果缓存存储端口。
 *
 * 封装聚合列表缓存读写与批量失效逻辑，
 * 避免应用层直接依赖 RedisTemplate 和缓存 key 细节。
 */
public interface NotificationAggregationStore {

    PageResult<AggregatedNotificationVO> get(Long userId, int page, int size);

    void set(Long userId, int page, int size, PageResult<AggregatedNotificationVO> result, Duration ttl);

    void evictUser(Long userId);
}
