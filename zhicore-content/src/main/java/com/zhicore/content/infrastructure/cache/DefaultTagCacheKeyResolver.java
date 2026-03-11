package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.application.port.cachekey.TagCacheKeyResolver;
import org.springframework.stereotype.Component;

/**
 * 默认标签缓存 key 解析实现。
 */
@Component
public class DefaultTagCacheKeyResolver implements TagCacheKeyResolver {

    @Override
    public String byId(Long tagId) {
        return TagRedisKeys.byId(tagId);
    }

    @Override
    public String bySlug(String slug) {
        return TagRedisKeys.bySlug(slug);
    }

    @Override
    public String list(int limit) {
        return "tag:list:" + limit;
    }

    @Override
    public String hotTags(int limit) {
        return TagRedisKeys.hotTags(limit);
    }

    @Override
    public String lockById(Long tagId) {
        return "lock:tag:id:" + tagId;
    }

    @Override
    public String lockBySlug(String slug) {
        return TagRedisKeys.lockBySlug(slug);
    }

    @Override
    public String lockHotTags(int limit) {
        return TagRedisKeys.lockHotTags(limit);
    }
}
