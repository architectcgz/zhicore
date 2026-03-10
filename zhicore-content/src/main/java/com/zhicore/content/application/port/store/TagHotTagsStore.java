package com.zhicore.content.application.port.store;

import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.content.application.dto.TagStatsDTO;

import java.time.Duration;
import java.util.List;

/**
 * 热门标签缓存存储端口。
 *
 * 封装热门标签列表的三态缓存读写与失效逻辑，
 * 避免应用层直接依赖 RedisTemplate。
 */
public interface TagHotTagsStore {

    CacheResult<List<TagStatsDTO>> getHotTags(int limit);

    void setHotTags(int limit, List<TagStatsDTO> hotTags, Duration ttl);

    void setEmptyHotTags(int limit, Duration ttl);

    void evictHotTags();
}
