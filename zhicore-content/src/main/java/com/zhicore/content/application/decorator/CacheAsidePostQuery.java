package com.zhicore.content.application.decorator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zhicore.common.cache.port.CacheRepository;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
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
    private final CacheProperties cacheProperties;

    /** 降级结果缓存 TTL（5 分钟） */
    private static final Duration DEGRADED_TTL = Duration.ofMinutes(5);

    public CacheAsidePostQuery(
            @Qualifier("postQueryService") PostQuery delegate,
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
    public PostDetailView getDetail(PostId postId) {
        String cacheKey = PostRedisKeys.detail(postId);
        String lockKey = PostRedisKeys.lockDetail(postId);

        // 1. 尝试从缓存获取
        CacheResult<PostDetailView> cached = cacheRepository.get(cacheKey, PostDetailView.class);
        if (cached.isHit()) {
            log.debug("Cache hit for post detail: {}", postId);
            return cached.getValue();
        }
        if (cached.isNull()) {
            return null;
        }

        // 2. 缓存未命中，获取锁防止击穿
        boolean lockAcquired = lockManager.tryLock(lockKey, getLockWaitTime(), getLockLeaseTime());

        if (!lockAcquired) {
            // 未获取到锁，降级直接查数据源
            log.debug("Failed to acquire lock, fallback to source: {}", postId);
            return delegate.getDetail(postId);
        }

        try {
            // 3. DCL：获取锁后再次检查缓存
            CacheResult<PostDetailView> retried = cacheRepository.get(cacheKey, PostDetailView.class);
            if (retried.isHit()) {
                return retried.getValue();
            }
            if (retried.isNull()) {
                return null;
            }

            // 4. 从数据源获取
            log.debug("Cache miss for post detail, fetching from source: {}", postId);
            PostDetailView view = delegate.getDetail(postId);

            // 5. 缓存结果
            if (view == null) {
                cacheRepository.setIfAbsent(cacheKey, null, getNullTtl());
            } else if (view.isContentDegraded()) {
                cacheRepository.set(cacheKey, view, DEGRADED_TTL);
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
    
    @Override
    public List<PostListItemView> getLatestPosts(Pageable pageable) {
        return getCachedList(
                PostRedisKeys.listLatest(pageable.getPageNumber(), pageable.getPageSize()),
                () -> delegate.getLatestPosts(pageable)
        );
    }
    
    @Override
    public List<PostListItemView> getPostsByAuthor(UserId authorId, Pageable pageable) {
        return getCachedList(
                PostRedisKeys.listAuthor(authorId, pageable.getPageNumber(), pageable.getPageSize()),
                () -> delegate.getPostsByAuthor(authorId, pageable)
        );
    }
    
    @Override
    public List<PostListItemView> getPostsByTag(TagId tagId, Pageable pageable) {
        return getCachedList(
                PostRedisKeys.listTag(tagId, pageable.getPageNumber(), pageable.getPageSize()),
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
        CacheResult<List<PostListItemView>> cached =
                cacheRepository.get(cacheKey, new TypeReference<List<PostListItemView>>() {});

        if (cached.isHit()) {
            log.debug("Cache hit for list: {}", cacheKey);
            return cached.getValue();
        }
        if (cached.isNull()) {
            return List.of();
        }

        log.debug("Cache miss for list, fetching from source: {}", cacheKey);
        List<PostListItemView> result = supplier.get();

        Duration ttl = getListTtl().plus(Duration.ofSeconds(
                ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
        cacheRepository.set(cacheKey, result, ttl);

        return result;
    }

    private Duration getDetailTtl() {
        return Duration.ofSeconds(cacheProperties.getTtl().getEntityDetail());
    }

    private Duration getListTtl() {
        return Duration.ofSeconds(cacheProperties.getTtl().getList());
    }

    private Duration getNullTtl() {
        return Duration.ofSeconds(cacheProperties.getTtl().getNullValue());
    }

    private Duration getLockWaitTime() {
        return Duration.ofSeconds(cacheProperties.getLock().getWaitTime());
    }

    private Duration getLockLeaseTime() {
        return Duration.ofSeconds(cacheProperties.getLock().getLeaseTime());
    }

    private int getJitterMaxSeconds() {
        return cacheProperties.getJitter().getMaxSeconds();
    }
}
