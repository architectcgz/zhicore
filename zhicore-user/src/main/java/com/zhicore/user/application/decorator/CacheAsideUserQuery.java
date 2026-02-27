package com.zhicore.user.application.decorator;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.cache.port.CacheRepository;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.port.UserQueryPort;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
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
    private final CacheRepository cacheRepository;
    private final LockManager lockManager;
    private final CacheProperties cacheProperties;

    private static final Duration DEFAULT_DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration DEFAULT_NULL_TTL = Duration.ofSeconds(60);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration LOCK_WAIT_TIME = Duration.ofMillis(200);

    public CacheAsideUserQuery(
            @Qualifier("userApplicationService") UserQueryPort delegate,
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
    public UserVO getUserById(Long userId) {
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);

        // 1. 尝试从缓存获取
        CacheResult<UserVO> cached = cacheRepository.get(cacheKey, UserVO.class);
        if (cached.isHit()) {
            log.debug("Cache hit for user detail: userId={}", userId);
            return cached.getValue();
        }
        if (cached.isNull()) {
            return null;
        }

        // 2. 缓存未命中，获取锁防止击穿
        boolean lockAcquired = lockManager.tryLock(lockKey, LOCK_WAIT_TIME, LOCK_TTL);

        if (!lockAcquired) {
            log.debug("Failed to acquire lock, fallback to source: userId={}", userId);
            return delegate.getUserById(userId);
        }

        try {
            // 3. DCL：获取锁后再次检查缓存
            CacheResult<UserVO> retried = cacheRepository.get(cacheKey, UserVO.class);
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
    public UserSimpleDTO getUserSimpleById(Long userId) {
        String cacheKey = UserRedisKeys.userSimple(userId);

        // 1. 尝试从缓存获取
        CacheResult<UserSimpleDTO> cached = cacheRepository.get(cacheKey, UserSimpleDTO.class);
        if (cached.isHit()) {
            log.debug("Cache hit for user simple: userId={}", userId);
            return cached.getValue();
        }
        if (cached.isNull()) {
            return null;
        }

        // 2. 缓存未命中，查数据源
        log.debug("Cache miss for user simple, fetching from source: userId={}", userId);
        UserSimpleDTO dto = delegate.getUserSimpleById(userId);

        // 使用 setIfAbsent 防止并发回填覆盖
        if (dto == null) {
            cacheRepository.setIfAbsent(cacheKey, null, getNullTtl());
        } else {
            Duration ttl = getDetailTtl().plus(Duration.ofSeconds(
                    ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
            cacheRepository.setIfAbsent(cacheKey, dto, ttl);
        }
        return dto;
    }

    @Override
    public Map<Long, UserSimpleDTO> batchGetUsersSimple(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, UserSimpleDTO> result = new HashMap<>();
        Set<Long> missedIds = new HashSet<>();

        // 1. 逐个查缓存
        for (Long userId : userIds) {
            String cacheKey = UserRedisKeys.userSimple(userId);
            CacheResult<UserSimpleDTO> cached = cacheRepository.get(cacheKey, UserSimpleDTO.class);
            if (cached.isHit()) {
                result.put(userId, cached.getValue());
            } else if (!cached.isNull()) {
                missedIds.add(userId);
            }
            // isNull → 防穿透，跳过该 ID
        }

        // 2. 批量查库回填 miss 的 ID
        if (!missedIds.isEmpty()) {
            Map<Long, UserSimpleDTO> fromDb = delegate.batchGetUsersSimple(missedIds);

            for (Long missedId : missedIds) {
                UserSimpleDTO dto = fromDb.get(missedId);
                String cacheKey = UserRedisKeys.userSimple(missedId);
                if (dto != null) {
                    // 每个 key 独立计算 jitter，避免批量回填同时过期引发雪崩
                    Duration ttl = getDetailTtl().plus(Duration.ofSeconds(
                            ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
                    cacheRepository.setIfAbsent(cacheKey, dto, ttl);
                    result.put(missedId, dto);
                } else {
                    cacheRepository.setIfAbsent(cacheKey, null, getNullTtl());
                }
            }
        }

        return result;
    }

    /**
     * 缓存回填（仅供持锁场景使用，非 null 值用 set 覆盖写）
     * 无锁场景应直接使用 setIfAbsent 防止并发覆盖
     */
    private void cacheValue(String key, Object value) {
        if (value == null) {
            cacheRepository.setIfAbsent(key, null, getNullTtl());
        } else {
            Duration ttl = getDetailTtl().plus(Duration.ofSeconds(
                    ThreadLocalRandom.current().nextInt(getJitterMaxSeconds())));
            cacheRepository.set(key, value, ttl);
        }
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