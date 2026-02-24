package com.zhicore.content.application.decorator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zhicore.content.application.port.cache.CacheRepository;
import com.zhicore.content.application.port.cache.LockManager;
import com.zhicore.content.application.query.PostQuery;
import com.zhicore.content.application.query.view.PostDetailView;
import com.zhicore.content.application.query.view.PostListItemView;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.cache.PostRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 缓存旁路查询服务装饰器
 * 
 * 装饰 PostQueryService，实现缓存策略：
 * - 缓存优先查询（先查缓存，未命中再查数据源）
 * - 分布式锁防止缓存击穿
 * - 空值缓存（1 分钟 TTL）
 * - 降级结果缓存（5 分钟 TTL）
 * - 正常结果缓存（30 分钟 + jitter）
 * - 列表查询缓存（10 分钟 + jitter）
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service
@Primary
public class CacheAsidePostQuery implements PostQuery {
    
    private final PostQuery delegate;
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
     * 空值缓存 TTL（1 分钟）
     */
    private static final Duration NULL_TTL = Duration.ofMinutes(1);
    
    /**
     * 降级结果缓存 TTL（5 分钟）
     */
    private static final Duration DEGRADED_TTL = Duration.ofMinutes(5);
    
    /**
     * 分布式锁 TTL（10 秒）
     */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    
    public CacheAsidePostQuery(
            @Qualifier("postQueryService") PostQuery delegate,
            CacheRepository cacheRepository,
            LockManager lockManager
    ) {
        this.delegate = delegate;
        this.cacheRepository = cacheRepository;
        this.lockManager = lockManager;
    }
    
    @Override
    public PostDetailView getDetail(PostId postId) {
        String cacheKey = PostRedisKeys.detail(postId);
        String lockKey = PostRedisKeys.lockDetail(postId);
        
        // 1. 尝试从缓存获取
        Optional<PostDetailView> cached = cacheRepository.get(cacheKey, PostDetailView.class);
        if (cached.isPresent()) {
            log.debug("Cache hit for post detail: {}", postId);
            return cached.get();
        }
        
        // 2. 缓存未命中，获取锁防止击穿
        // waitTime=0 表示不等待，leaseTime=10s 防止死锁
        boolean lockAcquired = lockManager.tryLock(
                lockKey,
                Duration.ZERO,      // 不等待
                LOCK_TTL            // 锁租期 10 秒
        );
        
        if (!lockAcquired) {
            // 未获取到锁，等待后重试缓存
            log.debug("Failed to acquire lock, waiting for cache: {}", postId);
            try {
                Thread.sleep(100);
                Optional<PostDetailView> retried = cacheRepository.get(cacheKey, PostDetailView.class);
                if (retried.isPresent()) {
                    return retried.get();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for cache: {}", postId);
            }
        }
        
        try {
            // 3. 从数据源获取
            log.debug("Cache miss for post detail, fetching from source: {}", postId);
            PostDetailView view = delegate.getDetail(postId);
            
            // 4. 缓存结果
            if (view == null) {
                // 空值缓存
                log.debug("Caching null value for post: {}", postId);
                cacheRepository.setIfAbsent(cacheKey, null, NULL_TTL);
            } else if (view.isContentDegraded()) {
                // 降级结果使用短 TTL
                log.debug("Caching degraded result for post: {}", postId);
                cacheRepository.set(cacheKey, view, DEGRADED_TTL);
            } else {
                // 正常结果使用标准 TTL + jitter
                Duration ttl = DETAIL_TTL.plus(Duration.ofSeconds(ThreadLocalRandom.current().nextInt(60)));
                log.debug("Caching normal result for post: {}, ttl: {}", postId, ttl);
                cacheRepository.set(cacheKey, view, ttl);
            }
            
            return view;
            
        } finally {
            if (lockAcquired) {
                lockManager.unlock(lockKey);
            }
        }
    }
    
    @Override
    public List<PostListItemView> getLatestPosts(Pageable pageable) {
        return getCachedList(
                PostRedisKeys.listLatest(pageable.getPageNumber()),
                () -> delegate.getLatestPosts(pageable)
        );
    }
    
    @Override
    public List<PostListItemView> getPostsByAuthor(UserId authorId, Pageable pageable) {
        return getCachedList(
                PostRedisKeys.listAuthor(authorId, pageable.getPageNumber()),
                () -> delegate.getPostsByAuthor(authorId, pageable)
        );
    }
    
    @Override
    public List<PostListItemView> getPostsByTag(TagId tagId, Pageable pageable) {
        return getCachedList(
                PostRedisKeys.listTag(tagId, pageable.getPageNumber()),
                () -> delegate.getPostsByTag(tagId, pageable)
        );
    }
    
    /**
     * 获取缓存的列表数据
     * 
     * 使用 TypeReference 处理泛型类型
     * 
     * @param cacheKey 缓存键
     * @param supplier 数据源提供者
     * @return 列表数据
     */
    private List<PostListItemView> getCachedList(String cacheKey, Supplier<List<PostListItemView>> supplier) {
        // 使用 TypeReference 处理泛型类型
        Optional<List<PostListItemView>> cached =
                cacheRepository.get(cacheKey, new TypeReference<List<PostListItemView>>() {});
        
        if (cached.isPresent()) {
            log.debug("Cache hit for list: {}", cacheKey);
            return cached.get();
        }
        
        log.debug("Cache miss for list, fetching from source: {}", cacheKey);
        List<PostListItemView> result = supplier.get();
        
        // 缓存结果，使用 TTL + jitter
        Duration ttl = LIST_TTL.plus(Duration.ofSeconds(ThreadLocalRandom.current().nextInt(60)));
        cacheRepository.set(cacheKey, result, ttl);
        
        return result;
    }
}
