package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.application.port.store.TagHotTagsStore;
import com.zhicore.content.application.port.store.TagStatsCacheStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Redis 的标签统计缓存失效实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTagStatsCacheStore implements TagStatsCacheStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final TagHotTagsStore tagHotTagsStore;

    @Override
    public void evictTagStats(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        for (Long tagId : tagIds) {
            String statsKey = TagRedisKeys.tagStats(tagId);
            redisTemplate.delete(statsKey);
            log.debug("Invalidated tag stats cache: key={}", statsKey);
        }
    }

    @Override
    public void evictHotTags() {
        tagHotTagsStore.evictHotTags();
        log.debug("Invalidated hot tags cache");
    }
}
