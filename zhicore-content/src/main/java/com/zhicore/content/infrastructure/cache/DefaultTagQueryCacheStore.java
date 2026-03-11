package com.zhicore.content.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.content.application.port.cachekey.TagCacheKeyResolver;
import com.zhicore.content.application.port.store.TagQueryCacheStore;
import com.zhicore.content.application.query.view.HotTagView;
import com.zhicore.content.application.query.view.TagDetailView;
import com.zhicore.content.application.query.view.TagListItemView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 默认标签查询缓存存储实现。
 */
@Component
@RequiredArgsConstructor
public class DefaultTagQueryCacheStore implements TagQueryCacheStore {

    private static final TypeReference<List<TagListItemView>> TAG_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<HotTagView>> HOT_TAG_LIST_TYPE = new TypeReference<>() {};

    private final CacheStore cacheStore;
    private final TagCacheKeyResolver tagCacheKeyResolver;

    @Override
    public CacheResult<TagDetailView> getDetail(Long tagId) {
        return cacheStore.get(tagCacheKeyResolver.byId(tagId), TagDetailView.class);
    }

    @Override
    public void setDetail(Long tagId, TagDetailView detailView, Duration ttl) {
        cacheStore.set(tagCacheKeyResolver.byId(tagId), detailView, ttl);
    }

    @Override
    public void setDetailNull(Long tagId, Duration ttl) {
        cacheStore.setIfAbsent(tagCacheKeyResolver.byId(tagId), null, ttl);
    }

    @Override
    public CacheResult<TagDetailView> getDetailBySlug(String slug) {
        return cacheStore.get(tagCacheKeyResolver.bySlug(slug), TagDetailView.class);
    }

    @Override
    public void setDetailBySlug(String slug, TagDetailView detailView, Duration ttl) {
        cacheStore.set(tagCacheKeyResolver.bySlug(slug), detailView, ttl);
    }

    @Override
    public void setDetailBySlugNull(String slug, Duration ttl) {
        cacheStore.setIfAbsent(tagCacheKeyResolver.bySlug(slug), null, ttl);
    }

    @Override
    public CacheResult<List<TagListItemView>> getList(int limit) {
        return cacheStore.get(tagCacheKeyResolver.list(limit), TAG_LIST_TYPE);
    }

    @Override
    public void setList(int limit, List<TagListItemView> tags, Duration ttl) {
        cacheStore.set(tagCacheKeyResolver.list(limit), tags, ttl);
    }

    @Override
    public CacheResult<List<HotTagView>> getHotTags(int limit) {
        return cacheStore.get(tagCacheKeyResolver.hotTags(limit), HOT_TAG_LIST_TYPE);
    }

    @Override
    public void setHotTags(int limit, List<HotTagView> hotTags, Duration ttl) {
        cacheStore.set(tagCacheKeyResolver.hotTags(limit), hotTags, ttl);
    }

    @Override
    public void setHotTagsEmpty(int limit, Duration ttl) {
        cacheStore.setIfAbsent(tagCacheKeyResolver.hotTags(limit), null, ttl);
    }
}
