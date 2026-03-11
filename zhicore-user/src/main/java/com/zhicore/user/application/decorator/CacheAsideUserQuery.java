package com.zhicore.user.application.decorator;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.common.cache.port.CacheStore;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.port.UserCacheKeyResolver;
import com.zhicore.user.application.port.UserQueryPort;
import com.zhicore.user.application.query.view.UserSimpleView;
import com.zhicore.user.application.sentinel.UserSentinelHandlers;
import com.zhicore.user.application.sentinel.UserSentinelResources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户查询缓存装饰器
 *
 * 装饰 UserQueryPort 的查询方法，实现缓存策略：
 * - Cache-Aside 模式
 * - 分布式锁防击穿（200ms 等待 + DCL）
 * - 空值缓存防穿透
 * - TTL jitter 防雪崩
 * - TTL 从 CacheProperties 配置读取
 */
@Slf4j
@Service
@Primary
public class CacheAsideUserQuery implements UserQueryPort {

    private final UserQueryPort delegate;
    private final CacheStore cacheStore;
    private final LockManager lockManager;
    private final CacheProperties cacheProperties;
    private final UserCacheKeyResolver userCacheKeyResolver;


    public CacheAsideUserQuery(
            @Qualifier("userQueryService") UserQueryPort delegate,
            CacheStore cacheStore,
            LockManager lockManager,
            CacheProperties cacheProperties,
            UserCacheKeyResolver userCacheKeyResolver
    ) {
        this.delegate = delegate;
        this.cacheStore = cacheStore;
        this.lockManager = lockManager;
        this.cacheProperties = cacheProperties;
        this.userCacheKeyResolver = userCacheKeyResolver;
    }

    @Override
    @SentinelResource(
            value = UserSentinelResources.GET_USER_DETAIL,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleGetUserDetailBlocked"
    )
    public UserVO getUserById(Long userId) {
        String cacheKey = userCacheKeyResolver.userDetail(userId);
        String lockKey = userCacheKeyResolver.lockDetail(userId);

        // 1. 尝试从缓存获取
        CacheResult<UserVO> cached = cacheStore.get(cacheKey, UserVO.class);
        if (cached.isHit()) {
            log.debug("Cache hit for user detail: userId={}", userId);
            return cached.getValue();
        }
        if (cached.isNull()) {
            return null;
        }

        // 2. 缓存未命中，获取锁防止击穿
        boolean lockAcquired = lockManager.tryLock(lockKey, getLockWaitTime(), getLockLeaseTime());

        if (!lockAcquired) {
            log.debug("Failed to acquire lock, fallback to source: userId={}", userId);
            return delegate.getUserById(userId);
        }

        try {
            // 3. DCL：获取锁后再次检查缓存
            CacheResult<UserVO> retried = cacheStore.get(cacheKey, UserVO.class);
            if (retried.isHit()) {
                return retried.getValue();
            }
            if (retried.isNull()) {
                return null;
            }

            // 4. 从数据源获取
            log.debug("Cache miss for user detail, fetching from source: userId={}", userId);
            UserVO userVO = delegate.getUserById(userId);

            // 5. 缓存结果
            cacheValue(cacheKey, userVO);
            return userVO;

        } finally {
            lockManager.unlock(lockKey);
        }
    }

    @Override
    @SentinelResource(
            value = UserSentinelResources.GET_USER_SIMPLE,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleGetUserSimpleBlocked"
    )
    public UserSimpleView getUserSimpleById(Long userId) {
        String cacheKey = userCacheKeyResolver.userSimple(userId);

        // 1. 尝试从缓存获取
        CacheResult<UserSimpleView> cached = cacheStore.get(cacheKey, UserSimpleView.class);
        if (cached.isHit()) {
            log.debug("Cache hit for user simple: userId={}", userId);
            return cached.getValue();
        }
        if (cached.isNull()) {
            return null;
        }

        // 2. 缓存未命中，查数据源
        log.debug("Cache miss for user simple, fetching from source: userId={}", userId);
        UserSimpleView dto = delegate.getUserSimpleById(userId);

        // 使用 setIfAbsent 防止并发回填覆盖
        if (dto == null) {
            cacheStore.setIfAbsent(cacheKey, null, getNullTtl());
        } else {
            Duration ttl = getDetailTtl().plus(Duration.ofSeconds(
                    ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
            cacheStore.setIfAbsent(cacheKey, dto, ttl);
        }
        return dto;
    }

    @Override
    @SentinelResource(
            value = UserSentinelResources.BATCH_GET_USERS_SIMPLE,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleBatchGetUsersSimpleBlocked"
    )
    public Map<Long, UserSimpleView> batchGetUsersSimple(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, UserSimpleView> result = new HashMap<>();
        Set<Long> missedIds = new HashSet<>();

        // 1. 逐个查缓存
        for (Long userId : userIds) {
            String cacheKey = userCacheKeyResolver.userSimple(userId);
            CacheResult<UserSimpleView> cached = cacheStore.get(cacheKey, UserSimpleView.class);
            if (cached.isHit()) {
                result.put(userId, cached.getValue());
            } else if (!cached.isNull()) {
                missedIds.add(userId);
            }
            // isNull → 防穿透，跳过该 ID
        }

        // 2. 批量查库回填 miss 的 ID
        if (!missedIds.isEmpty()) {
            Map<Long, UserSimpleView> fromDb = delegate.batchGetUsersSimple(missedIds);

            for (Long missedId : missedIds) {
                UserSimpleView dto = fromDb.get(missedId);
                String cacheKey = userCacheKeyResolver.userSimple(missedId);
                if (dto != null) {
                    // 每个 key 独立计算 jitter，避免批量回填同时过期引发雪崩
                    Duration ttl = getDetailTtl().plus(Duration.ofSeconds(
                            ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
                    cacheStore.setIfAbsent(cacheKey, dto, ttl);
                    result.put(missedId, dto);
                } else {
                    cacheStore.setIfAbsent(cacheKey, null, getNullTtl());
                }
            }
        }

        return result;
    }

    @Override
    @SentinelResource(
            value = UserSentinelResources.GET_STRANGER_MESSAGE_SETTING,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleGetStrangerMessageSettingBlocked"
    )
    public boolean isStrangerMessageAllowed(Long userId) {
        String cacheKey = userCacheKeyResolver.strangerMessageSetting(userId);

        CacheResult<Boolean> cached = cacheStore.get(cacheKey, Boolean.class);
        if (cached.isHit()) {
            log.debug("Cache hit for stranger message setting: userId={}", userId);
            return Boolean.TRUE.equals(cached.getValue());
        }

        log.debug("Cache miss for stranger message setting, fetching from source: userId={}", userId);
        boolean allowed = delegate.isStrangerMessageAllowed(userId);
        Duration ttl = getDetailTtl().plus(Duration.ofSeconds(
                ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
        cacheStore.set(cacheKey, allowed, ttl);
        return allowed;
    }

    /**
     * 缓存回填（仅供持锁场景使用，非 null 值用 set 覆盖写）
     * 无锁场景应直接使用 setIfAbsent 防止并发覆盖
     */
    private void cacheValue(String key, Object value) {
        if (value == null) {
            cacheStore.setIfAbsent(key, null, getNullTtl());
        } else {
            Duration ttl = getDetailTtl().plus(Duration.ofSeconds(
                    ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
            cacheStore.set(key, value, ttl);
        }
    }

    private Duration getDetailTtl() {
        return Duration.ofSeconds(cacheProperties.getTtl().getEntityDetail());
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
