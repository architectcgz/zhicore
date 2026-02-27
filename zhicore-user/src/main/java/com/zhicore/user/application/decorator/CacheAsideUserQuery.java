package com.zhicore.user.application.decorator;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.cache.port.CacheRepository;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.service.UserApplicationService;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户查询缓存装饰器
 *
 * 装饰 UserApplicationService 的查询方法，实现缓存策略：
 * - Cache-Aside 模式
 * - 分布式锁防击穿（200ms 等待 + DCL）
 * - 空值缓存防穿透
 * - TTL jitter 防雪崩
 * - TTL 从 CacheProperties 配置读取
 */
@Slf4j
@Component
public class CacheAsideUserQuery {

    private final CacheRepository cacheRepository;
    private final LockManager lockManager;
    private final CacheProperties cacheProperties;
    private final UserApplicationService userApplicationService;

    private static final Duration DEFAULT_DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration DEFAULT_NULL_TTL = Duration.ofSeconds(60);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration LOCK_WAIT_TIME = Duration.ofMillis(200);

    public CacheAsideUserQuery(
            CacheRepository cacheRepository,
            LockManager lockManager,
            CacheProperties cacheProperties,
            UserApplicationService userApplicationService
    ) {
        this.cacheRepository = cacheRepository;
        this.lockManager = lockManager;
        this.cacheProperties = cacheProperties;
        this.userApplicationService = userApplicationService;
    }

    /**
     * 获取用户详情（带缓存）
     *
     * @param userId 用户ID
     * @return 用户视图对象
     */
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
            // 未获取到锁，降级直接查数据源
            log.debug("Failed to acquire lock, fallback to source: userId={}", userId);
            return userApplicationService.getUserById(userId);
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
            UserVO userVO = userApplicationService.getUserById(userId);

            // 5. 缓存结果
            cacheValue(cacheKey, userVO);
            return userVO;

        } finally {
            lockManager.unlock(lockKey);
        }
    }

    /**
     * 获取用户简要信息（带缓存）
     *
     * @param userId 用户ID
     * @return 用户简要信息DTO
     */
    public UserSimpleDTO getUserSimpleById(Long userId) {
        String cacheKey = UserRedisKeys.userDetail(userId) + ":simple";

        // 1. 尝试从缓存获取
        CacheResult<UserSimpleDTO> cached = cacheRepository.get(cacheKey, UserSimpleDTO.class);
        if (cached.isHit()) {
            log.debug("Cache hit for user simple: userId={}", userId);
            return cached.getValue();
        }
        if (cached.isNull()) {
            return null;
        }

        // 2. 缓存未命中，直接查数据源（简要信息不需要锁保护）
        log.debug("Cache miss for user simple, fetching from source: userId=", userId);
        UserSimpleDTO dto = userApplicationService.getUserSimpleById(userId);

        cacheValue(cacheKey, dto);
        return dto;
    }

    /**
     * 失效用户缓存（写操作后调用）
     */
    public void evictUserCache(Long userId) {
        try {
            cacheRepository.delete(
                    UserRedisKeys.userDetail(userId),
                    UserRedisKeys.userDetail(userId) + ":simple"
            );
            log.debug("Evicted user cache: userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to evict user cache: userId={}, error={}", userId, e.getMessage());
        }
    }

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