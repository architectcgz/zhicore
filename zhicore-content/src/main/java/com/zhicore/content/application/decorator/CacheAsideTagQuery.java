package com.zhicore.content.application.decorator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zhicore.common.cache.port.CacheRepository;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.content.application.query.TagQuery;
import com.zhicore.content.application.query.view.HotTagView;
import com.zhicore.content.application.query.view.TagDetailView;
import com.zhicore.content.application.query.view.TagListItemView;
import com.zhicore.content.infrastructure.cache.TagRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 缓存旁路标签查询服务装饰器
 * 
 * 装饰 TagQueryService，实现缓存策略：
 * - 缓存优先查询（先查缓存，未命中再查数据源）
 * - 分布式锁防止缓存击穿
 * - 空值缓存（1 分钟 TTL）
 * - 正常结果缓存（30 分钟 + jitter）
 * - 列表查询缓存（10 分钟 + jitter）
 * - 热门标签缓存（1 小时 + jitter）
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service
@Primary
public class CacheAsideTagQuery implements TagQuery {

    private final TagQuery delegate;
    private final CacheRepository cacheRepository;
    private final LockManager lockManager;
    private final CacheProperties cacheProperties;

    /** 详情缓存 TTL 默认值（30 分钟） */
    private static final Duration DEFAULT_DETAIL_TTL = Duration.ofMinutes(30);
    /** 列表缓存 TTL 默认值（10 分钟） */
    private static final Duration DEFAULT_LIST_TTL = Duration.ofMinutes(10);
    /** 热门标签缓存 TTL 默认值（1 小时） */
    private static final Duration DEFAULT_HOT_TAGS_TTL = Duration.ofHours(1);
    /** 空值缓存 TTL 默认值（5 分钟） */
    private static final Duration DEFAULT_NULL_TTL = Duration.ofMinutes(5);
    /** 分布式锁 TTL（10 秒） */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    /** 锁等待时间（200 毫秒） */
    private static final Duration LOCK_WAIT_TIME = Duration.ofMillis(200);

    public CacheAsideTagQuery(
            @Qualifier("tagQueryService") TagQuery delegate,
            CacheRepository cacheRepository,
            LockManager lockManager,
            CacheProperties cacheProperties
    ) {
        this.delegate = delegate;
        this.cacheRepository = cacheRepository;
        this.lockManager = lockManager;
        this.cacheProperties = cacheProperties;
    }
    
    @Override
    public TagDetailView getDetail(Long tagId) {
        String cacheKey = TagRedisKeys.byId(tagId);
        String lockKey = TagRedisKeys.lockBySlug(String.valueOf(tagId));
        
        return getCachedDetail(cacheKey, lockKey, () -> delegate.getDetail(tagId));
    }
    
    @Override
    public TagDetailView getDetailBySlug(String slug) {
        String cacheKey = TagRedisKeys.bySlug(slug);
        String lockKey = TagRedisKeys.lockBySlug(slug);
        
        return getCachedDetail(cacheKey, lockKey, () -> delegate.getDetailBySlug(slug));
    }
    
    @Override
    public List<TagListItemView> getList(int limit) {
        String cacheKey = "tag:list:" + limit;
        
        return getCachedList(
                cacheKey,
                () -> delegate.getList(limit),
                LIST_TTL
        );
    }
    
    @Override
    public List<TagListItemView> searchByName(String keyword, int limit) {
        // 搜索结果不缓存，直接查询数据源
        log.debug("Searching tags by name, no cache: keyword={}, limit={}", keyword, limit);
        return delegate.searchByName(keyword, limit);
    }
    
    @Override
    public List<HotTagView> getHotTags(int limit) {
        String cacheKey = TagRedisKeys.hotTags(limit);
        String lockKey = TagRedisKeys.lockHotTags(limit);

        // 1. 尝试从缓存获取
        CacheResult<List<HotTagView>> cached =
                cacheRepository.get(cacheKey, new TypeReference<List<HotTagView>>() {});

        if (cached.isHit()) {
            log.debug("Cache hit for hot tags: limit={}", limit);
            return cached.getValue();
        }
        if (cached.isNull()) {
            return List.of();
        }

        // 2. 缓存未命中，获取锁防止击穿（等待 200ms 让 Redisson 排队）
        boolean lockAcquired = lockManager.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_TTL);

        if (!lockAcquired) {
            // 未获取到锁，降级直接查数据源
            log.debug("Failed to acquire lock, fallback to source: limit={}", limit);
            return delegate.getHotTags(limit);
        }

        try {
            // 3. DCL：获取锁后再次检查缓存
            CacheResult<List<HotTagView>> retried =
                    cacheRepository.get(cacheKey, new TypeReference<List<HotTagView>>() {});
            if (retried.isHit()) {
                return retried.getValue();
            }
            if (retried.isNull()) {
                return List.of();
            }

            // 4. 从数据源获取
            log.debug("Cache miss for hot tags, fetching from source: limit={}", limit);
            List<HotTagView> result = delegate.getHotTags(limit);

            // 5. 缓存结果
            if (result == null || result.isEmpty()) {
                cacheRepository.setIfAbsent(cacheKey, result, getNullTtl());
            } else {
                Duration ttl = DEFAULT_HOT_TAGS_TTL.plus(Duration.ofSeconds(
                        ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
                cacheRepository.set(cacheKey, result, ttl);
            }

            return result;

        } finally {
            lockManager.unlock(lockKey);
        }
    }
    
    /**
     * 获取缓存的详情数据
     * 
     * 使用分布式锁防止缓存击穿
     * 
     * @param cacheKey 缓存键
     * @param lockKey 锁键
     * @param supplier 数据源提供者
     * @return 详情数据
     */
    private TagDetailView getCachedDetail(String cacheKey, String lockKey, Supplier<TagDetailView> supplier) {
        // 1. 尝试从缓存获取
        CacheResult<TagDetailView> cached = cacheRepository.get(cacheKey, TagDetailView.class);
        if (cached.isHit()) {
            log.debug("Cache hit for tag detail: {}", cacheKey);
            return cached.getValue();
        }
        if (cached.isNull()) {
            return null;
        }

        // 2. 缓存未命中，获取锁防止击穿（等待 200ms 让 Redisson 排队）
        boolean lockAcquired = lockManager.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_TTL);

        if (!lockAcquired) {
            // 未获取到锁，降级直接查数据源
            log.debug("Failed to acquire lock, fallback to source: {}", cacheKey);
            return supplier.get();
        }

        try {
            // 3. DCL：获取锁后再次检查缓存
            CacheResult<TagDetailView> retried = cacheRepository.get(cacheKey, TagDetailView.class);
            if (retried.isHit()) {
                return retried.getValue();
            }
            if (retried.isNull()) {
                return null;
            }

            // 4. 从数据源获取
            log.debug("Cache miss for tag detail, fetching from source: {}", cacheKey);
            TagDetailView view = supplier.get();

            // 5. 缓存结果
            if (view == null) {
                cacheRepository.setIfAbsent(cacheKey, null, getNullTtl());
            } else {
                Duration ttl = getDetailTtl().plus(Duration.ofSeconds(
                        ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
                cacheRepository.set(cacheKey, view, ttl);
            }

            return view;

        } finally {
            lockManager.unlock(lockKey);
        }
    }
    
    /**
     * 获取缓存的列表数据
     * 
     * 使用 TypeReference 处理泛型类型
     * 
     * @param cacheKey 缓存键
     * @param supplier 数据源提供者
     * @param baseTtl 基础 TTL
     * @return 列表数据
     */
    private List<TagListItemView> getCachedList(String cacheKey, Supplier<List<TagListItemView>> supplier, Duration baseTtl) {
        CacheResult<List<TagListItemView>> cached =
                cacheRepository.get(cacheKey, new TypeReference<List<TagListItemView>>() {});

        if (cached.isHit()) {
            log.debug("Cache hit for list: {}", cacheKey);
            return cached.getValue();
        }
        if (cached.isNull()) {
            return List.of();
        }

        log.debug("Cache miss for list, fetching from source: {}", cacheKey);
        List<TagListItemView> result = supplier.get();

        Duration ttl = baseTtl.plus(Duration.ofSeconds(
                ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
        cacheRepository.set(cacheKey, result, ttl);

        return result;
    }

    private Duration getDetailTtl() {
        long seconds = cacheProperties.getTtl().getEntityDetail();
        return seconds > 0 ? Duration.ofSeconds(seconds) : DEFAULT_DETAIL_TTL;
    }

    private Duration getNullTtl() {
        long seconds = cacheProperties.getTtl().getNullValue();
        return seconds > 0 ? Duration.ofSeconds(seconds) : DEFAULT_NULL_TTL;
    }

    private int getJitterMaxSeconds() {
        int max = cacheProperties.getJitter().getMaxSeconds();
        return max > 0 ? max : 60;
    }
}
