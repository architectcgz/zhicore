package com.zhicore.content.application.port.cachekey;

/**
 * 标签缓存 key 解析端口。
 */
public interface TagCacheKeyResolver {

    String byId(Long tagId);

    String bySlug(String slug);

    String list(int limit);

    String hotTags(int limit);

    String lockById(Long tagId);

    String lockBySlug(String slug);

    String lockHotTags(int limit);
}
