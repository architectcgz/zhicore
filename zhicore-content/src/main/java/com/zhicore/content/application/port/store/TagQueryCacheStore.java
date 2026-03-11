package com.zhicore.content.application.port.store;

import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.content.application.query.view.HotTagView;
import com.zhicore.content.application.query.view.TagDetailView;
import com.zhicore.content.application.query.view.TagListItemView;

import java.time.Duration;
import java.util.List;

/**
 * 标签查询缓存存储端口。
 *
 * 封装标签详情、列表与热门标签缓存的三态读写逻辑，
 * 避免应用层直接依赖通用缓存实现和 key 解析细节。
 */
public interface TagQueryCacheStore {

    CacheResult<TagDetailView> getDetail(Long tagId);

    void setDetail(Long tagId, TagDetailView detailView, Duration ttl);

    void setDetailNull(Long tagId, Duration ttl);

    CacheResult<TagDetailView> getDetailBySlug(String slug);

    void setDetailBySlug(String slug, TagDetailView detailView, Duration ttl);

    void setDetailBySlugNull(String slug, Duration ttl);

    CacheResult<List<TagListItemView>> getList(int limit);

    void setList(int limit, List<TagListItemView> tags, Duration ttl);

    CacheResult<List<HotTagView>> getHotTags(int limit);

    void setHotTags(int limit, List<HotTagView> hotTags, Duration ttl);

    void setHotTagsEmpty(int limit, Duration ttl);
}
