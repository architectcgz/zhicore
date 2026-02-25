package com.zhicore.content.application.decorator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zhicore.content.application.port.cache.CacheRepository;
import com.zhicore.content.application.port.cache.CacheResult;
import com.zhicore.content.application.port.cache.LockManager;
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
    
    /**
     * 详情缓存 TTL（30 分钟）
     */
    private static final Duration DETAIL_TTL = Duration.ofMinutes(30);
    
    /**
     * 列表缓存 TTL（10 分钟）
     */
    private static final Duration LIST_TTL = Duration.ofMinutes(10);
    
    /**
     * 热门标签缓存 TTL（1 小时）
     */
    private static final Duration HOT_TAGS_TTL = Duration.ofHours(1);
    
    /**
     * 空值缓存 TTL（5 分钟）
     */
    private static final Duration NULL_TTL = Duration.ofMinutes(5);
    
    /**
     * 分布式锁 TTL（10 秒）
     */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    
    public CacheAsideTagQuery(
            @Qualifier("tagQueryService") TagQuery delegate,
            CacheRepository cacheRepository,
            LockManager lockManager
    ) {
        this.delegate = delegate;
        this.cacheRepository = cacheRepository;
        this.lockManager = lockManager;
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
        
        // 2. 缓存未命中，获取锁防止击穿
        boolean lockAcquired = lockManager.tryLock(
                lockKey,
                Duration.ZERO,      // 不等待
                LOCK_TTL            // 锁租期 10 秒
        );
        
        if (!lockAcquired) {
            // 未获取到锁，等待后重试缓存
            log.debug("Failed to acquire lock, waiting for cache: limit={}", limit);
            try {
                Thread.sleep(100);
                CacheResult<List<HotTagView>> retried =
                        cacheRepository.get(cacheKey, new TypeReference<List<HotTagView>>() {});
                if (retried.isHit()) {
                    return retried.getValue();
                }
                if (retried.isNull()) {
                    return List.of();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for cache: limit={}", limit);
            }
        }
        
        try {
            // 3. 从数据源获取
            log.debug("Cache miss for hot tags, fetching from source: limit={}", limit);
            List<HotTagView> result = delegate.getHotTags(limit);
            
            // 4. 缓存结果
            if (result == null || result.isEmpty()) {
                // 空值缓存
                log.debug("Caching empty hot tags: limit={}", limit);
                cacheRepository.setIfAbsent(cacheKey, result, NULL_TTL);
            } else {
                // 正常结果使用标准 TTL + jitter
                Duration ttl = HOT_TAGS_TTL.plus(Duration.ofSeconds(ThreadLocalRandom.current().nextInt(300)));
                log.debug("Caching hot tags: limit={}, ttl={}", limit, ttl);
                cacheRepository.set(cacheKey, result, ttl);
            }
            
            return result;
            
        } finally {
            if (lockAcquired) {
                lockManager.unlock(lockKey);
            }
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
        
        // 2. 缓存未命中，获取锁防止击穿
        boolean lockAcquired = lockManager.tryLock(
                lockKey,
                Duration.ZERO,      // 不等待
                LOCK_TTL            // 锁租期 10 秒
        );
        
        if (!lockAcquired) {
            // 未获取到锁，等待后重试缓存
            log.debug("Failed to acquire lock, waiting for cache: {}", cacheKey);
            try {
                Thread.sleep(100);
                CacheResult<TagDetailView> retried = cacheRepository.get(cacheKey, TagDetailView.class);
                if (retried.isHit()) {
                    return retried.getValue();
                }
                if (retried.isNull()) {
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for cache: {}", cacheKey);
            }
        }
        
        try {
            // 3. 从数据源获取
            log.debug("Cache miss for tag detail, fetching from source: {}", cacheKey);
            TagDetailView view = supplier.get();
            
            // 4. 缓存结果
            if (view == null) {
                // 空值缓存
                log.debug("Caching null value for tag: {}", cacheKey);
                cacheRepository.setIfAbsent(cacheKey, null, NULL_TTL);
            } else {
                // 正常结果使用标准 TTL + jitter
                Duration ttl = DETAIL_TTL.plus(Duration.ofSeconds(ThreadLocalRandom.current().nextInt(60)));
                log.debug("Caching normal result for tag: {}, ttl: {}", cacheKey, ttl);
                cacheRepository.set(cacheKey, view, ttl);
            }
            
            return view;
            
        } finally {
            if (lockAcquired) {
                lockManager.unlock(lockKey);
            }
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
        // 使用 TypeReference 处理泛型类型
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
        
        // 缓存结果，使用 TTL + jitter
        Duration ttl = baseTtl.plus(Duration.ofSeconds(ThreadLocalRandom.current().nextInt(60)));
        cacheRepository.set(cacheKey, result, ttl);
        
        return result;
    }
}
