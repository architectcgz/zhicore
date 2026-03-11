package com.zhicore.content.application.port.store;

import java.util.List;

/**
 * 标签统计缓存存储端口。
 *
 * 封装标签统计缓存与热门标签缓存的失效逻辑，
 * 避免事件处理器直接依赖 Redis Key 细节。
 */
public interface TagStatsCacheStore {

    void evictTagStats(List<Long> tagIds);

    void evictHotTags();
}
