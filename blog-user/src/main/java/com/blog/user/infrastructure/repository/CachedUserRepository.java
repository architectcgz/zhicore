package com.blog.user.infrastructure.repository;

import com.blog.common.cache.CacheConstants;
import com.blog.common.cache.HotDataIdentifier;
import com.blog.common.config.CacheProperties;
import com.blog.user.domain.model.User;
import com.blog.user.domain.repository.UserRepository;
import com.blog.user.infrastructure.cache.UserRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 带缓存的用户仓储
 * 
 * 使用装饰器模式包装 UserRepositoryImpl，添加缓存功能
 * 实现 Cache-Aside 模式
 *
 * @author Blog Team
 */
@Slf4j
@Primary
@Repository
public class CachedUserRepository implements UserRepository {

    private final UserRepository delegate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;
    private final RedissonClient redissonClient;
    private final HotDataIdentifier hotDataIdentifier;

    public CachedUserRepository(
            RedisTemplate<String, Object> redisTemplate,
            CacheProperties cacheProperties,
            RedissonClient redissonClient,
            HotDataIdentifier hotDataIdentifier,
            @Qualifier("userRepositoryImpl") UserRepository delegate) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
        this.redissonClient = redissonClient;
        this.hotDataIdentifier = hotDataIdentifier;
        this.delegate = delegate;
    }

    /**
     * 获取分布式锁
     * 根据配置选择公平锁或非公平锁
     * 
     * @param lockKey 锁键
     * @return Redisson 锁对象
     */
    private RLock getLock(String lockKey) {
        if (cacheProperties.getLock().isFair()) {
            log.debug("Using fair lock for key: {}", lockKey);
            return redissonClient.getFairLock(lockKey);
        } else {
            log.debug("Using non-fair lock for key: {}", lockKey);
            return redissonClient.getLock(lockKey);
        }
    }

    /**
     * 获取锁的等待队列长度
     * 用于监控锁的竞争情况
     * 
     * 注意：Redisson RLock 接口不直接提供 getQueueLength() 方法
     * 此方法仅用于接口兼容性，实际返回 -1 表示不支持
     * 
     * @param lockKey 锁键
     * @return 等待队列长度，-1 表示不支持或获取失败
     */
    public int getLockQueueLength(String lockKey) {
        log.debug("Lock queue length monitoring not supported for key: {}", lockKey);
        return -1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<User> findById(Long userId) {
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        // Step 1: 第一次检查缓存
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            
            if (cached != null) {
                if (CacheConstants.NULL_VALUE.equals(cached)) {
                    log.debug("Cache hit (null value): key={}", cacheKey);
                    return Optional.empty();
                }
                // 尝试转换为 User 对象
                if (cached instanceof User) {
                    log.debug("Cache hit: key={}", cacheKey);
                    return Optional.of((User) cached);
                } else {
                    // 缓存数据类型不匹配，删除并重新查询
                    log.warn("Cache type mismatch for key={}, deleting and re-fetching", cacheKey);
                    redisTemplate.delete(cacheKey);
                }
            }
        } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            // Redis 连接失败，降级直接查询数据库
            log.error("Redis connection failed, falling back to database: userId={}, error={}", 
                    userId, e.getMessage());
            return delegate.findById(userId);
        } catch (Exception e) {
            // 其他异常，降级直接查询数据库
            log.error("Cache lookup failed, falling back to database: userId={}, error={}", 
                    userId, e.getMessage(), e);
            return delegate.findById(userId);
        }
        
        // Step 2: 缓存未命中，记录访问并检查是否为热点数据
        log.debug("Cache miss: key={}", cacheKey);
        
        try {
            hotDataIdentifier.recordAccess("user", userId);
        } catch (Exception e) {
            log.warn("Failed to record access for hot data identification: userId={}, error={}", 
                    userId, e.getMessage());
        }
        
        // 检查是否为热点数据（自动识别或手动标记）
        boolean isHot = false;
        try {
            isHot = hotDataIdentifier.isHotData("user", userId) 
                    || hotDataIdentifier.isManuallyMarkedAsHot("user", userId);
        } catch (Exception e) {
            log.warn("Failed to check hot data status: userId={}, error={}", userId, e.getMessage());
        }
        
        if (!isHot) {
            // 非热点数据：直接查询数据库并缓存
            log.debug("Non-hot data, direct database query: userId={}", userId);
            return loadAndCacheUser(userId, cacheKey);
        }
        
        // Step 3: 热点数据，使用分布式锁防止缓存击穿
        log.debug("Hot data detected, using distributed lock: userId={}", userId);
        RLock lock = getLock(lockKey);
        
        try {
            // 尝试获取锁（等待5秒，持有10秒）
            boolean acquired = lock.tryLock(
                    cacheProperties.getLock().getWaitTime(),
                    cacheProperties.getLock().getLeaseTime(),
                    TimeUnit.SECONDS
            );
            
            if (!acquired) {
                // Step 6: 超时降级 - 直接查询数据库
                log.warn("Failed to acquire lock within timeout for userId={}, falling back to database", userId);
                return delegate.findById(userId);
            }
            
            try {
                // Step 4: DCL 双重检查 - 获取锁后再次检查缓存
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (CacheConstants.NULL_VALUE.equals(cached)) {
                        log.debug("DCL: Cache hit (null value) after acquiring lock: key={}", cacheKey);
                        return Optional.empty();
                    }
                    if (cached instanceof User) {
                        log.debug("DCL: Cache hit after acquiring lock: key={}", cacheKey);
                        return Optional.of((User) cached);
                    }
                }
                
                // Step 5: 查询数据库并写入缓存
                log.debug("DCL: Cache still miss, querying database: userId={}", userId);
                Optional<User> userOpt = delegate.findById(userId);
                
                // 写入缓存
                try {
                    if (userOpt.isPresent()) {
                        // 缓存数据（TTL + 随机抖动）
                        long ttlWithJitter = cacheProperties.getTtl().getEntityDetail() + randomJitter();
                        redisTemplate.opsForValue().set(cacheKey, userOpt.get(), ttlWithJitter, TimeUnit.SECONDS);
                        log.debug("Cached user: key={}, ttl={}s", cacheKey, ttlWithJitter);
                    } else {
                        // 缓存空值（60秒 TTL）
                        redisTemplate.opsForValue().set(
                                cacheKey,
                                CacheConstants.NULL_VALUE,
                                cacheProperties.getTtl().getNullValue(),
                                TimeUnit.SECONDS
                        );
                        log.debug("Cached null value: key={}, ttl={}s", cacheKey, cacheProperties.getTtl().getNullValue());
                    }
                } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
                    log.error("Redis connection failed during cache write: userId={}, error={}", 
                            userId, e.getMessage());
                    // 缓存写入失败不影响业务
                } catch (Exception e) {
                    log.warn("Failed to cache user after database query: userId={}, error={}", 
                            userId, e.getMessage());
                    // 缓存写入失败不影响业务
                }
                
                return userOpt;
                
            } finally {
                // 确保锁被释放
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("Released lock: key={}", lockKey);
                    }
                } catch (Exception e) {
                    log.error("Failed to release lock: {}, will rely on auto-expiration", lockKey, e);
                    // 锁释放失败，依赖自动过期机制
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted for userId={}, falling back to database", userId);
            return delegate.findById(userId);
        } catch (org.redisson.client.RedisException e) {
            // Redisson 连接异常，降级查询数据库
            log.error("Redisson connection failed for userId={}, falling back to database: {}", 
                    userId, e.getMessage());
            return delegate.findById(userId);
        } catch (Exception e) {
            log.error("Unexpected error during cache operation for userId={}: {}", userId, e.getMessage(), e);
            // 异常时降级查询数据库
            return delegate.findById(userId);
        }
    }
    
    /**
     * 加载用户并缓存（非热点数据使用）
     */
    private Optional<User> loadAndCacheUser(Long userId, String cacheKey) {
        Optional<User> userOpt = delegate.findById(userId);
        
        // 写入缓存
        try {
            if (userOpt.isPresent()) {
                cacheUser(cacheKey, userOpt.get());
            } else {
                cacheUser(cacheKey, null);
            }
        } catch (Exception e) {
            log.warn("Failed to cache user: {}", e.getMessage());
        }
        
        return userOpt;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        // 邮箱查询不走缓存，直接查数据库
        return delegate.findByEmail(email);
    }

    @Override
    public List<User> findByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 批量查询直接走数据库，不走缓存
        return delegate.findByIds(userIds);
    }

    /**
     * 批量查询用户（带缓存优化）
     * 
     * 优化策略：
     * 1. 批量查询缓存
     * 2. 区分热点数据和非热点数据
     * 3. 热点数据使用分布式锁（按ID排序避免死锁）
     * 4. 非热点数据直接批量查询数据库
     * 
     * @param userIds 用户ID集合
     * @return 用户列表
     */
    public List<User> findByIdsWithCache(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<User> result = new ArrayList<>();
        Set<Long> cacheMissIds = new java.util.HashSet<>();
        Set<Long> hotDataIds = new java.util.HashSet<>();
        Set<Long> nonHotDataIds = new java.util.HashSet<>();

        // Step 1: 批量查询缓存
        for (Long userId : userIds) {
            String cacheKey = UserRedisKeys.userDetail(userId);
            try {
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (!CacheConstants.NULL_VALUE.equals(cached) && cached instanceof User) {
                        result.add((User) cached);
                    }
                    // 空值缓存或已缓存，跳过
                } else {
                    cacheMissIds.add(userId);
                }
            } catch (Exception e) {
                log.warn("Cache lookup failed for userId={}: {}", userId, e.getMessage());
                cacheMissIds.add(userId);
            }
        }

        if (cacheMissIds.isEmpty()) {
            log.debug("Batch query: all {} users found in cache", userIds.size());
            return result;
        }

        log.debug("Batch query: {} cache hits, {} cache misses", result.size(), cacheMissIds.size());

        // Step 2: 区分热点数据和非热点数据
        for (Long userId : cacheMissIds) {
            hotDataIdentifier.recordAccess("user", userId);
            boolean isHot = hotDataIdentifier.isHotData("user", userId) 
                    || hotDataIdentifier.isManuallyMarkedAsHot("user", userId);
            
            if (isHot) {
                hotDataIds.add(userId);
            } else {
                nonHotDataIds.add(userId);
            }
        }

        log.debug("Batch query: {} hot data, {} non-hot data", hotDataIds.size(), nonHotDataIds.size());

        // Step 3: 非热点数据直接批量查询数据库
        if (!nonHotDataIds.isEmpty()) {
            try {
                List<User> nonHotUsers = delegate.findByIds(nonHotDataIds);
                result.addAll(nonHotUsers);
                
                // 缓存非热点数据
                for (User user : nonHotUsers) {
                    try {
                        cacheUser(UserRedisKeys.userDetail(user.getId()), user);
                    } catch (Exception e) {
                        log.warn("Failed to cache non-hot user {}: {}", user.getId(), e.getMessage());
                    }
                }
                
                // 缓存不存在的用户（空值）
                Set<Long> foundIds = nonHotUsers.stream()
                        .map(User::getId)
                        .collect(java.util.stream.Collectors.toSet());
                for (Long userId : nonHotDataIds) {
                    if (!foundIds.contains(userId)) {
                        try {
                            cacheUser(UserRedisKeys.userDetail(userId), null);
                        } catch (Exception e) {
                            log.warn("Failed to cache null value for userId={}: {}", userId, e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to query non-hot users: {}", e.getMessage(), e);
            }
        }

        // Step 4: 热点数据使用分布式锁（按ID排序避免死锁）
        if (!hotDataIds.isEmpty()) {
            List<Long> sortedHotIds = new ArrayList<>(hotDataIds);
            java.util.Collections.sort(sortedHotIds);
            
            for (Long userId : sortedHotIds) {
                try {
                    Optional<User> userOpt = findById(userId);
                    userOpt.ifPresent(result::add);
                } catch (Exception e) {
                    log.error("Failed to query hot user {}: {}", userId, e.getMessage(), e);
                }
            }
        }

        log.debug("Batch query completed: {} users found", result.size());
        return result;
    }

    @Override
    public Optional<User> findByUserName(String userName) {
        // 用户名查询不走缓存，直接查数据库
        return delegate.findByUserName(userName);
    }

    @Override
    public User save(User user) {
        User saved = delegate.save(user);
        // 保存后缓存用户
        try {
            cacheUser(UserRedisKeys.userDetail(user.getId()), saved);
        } catch (Exception e) {
            log.warn("Failed to cache user after save: {}", e.getMessage());
        }
        return saved;
    }

    @Override
    public void update(User user) {
        delegate.update(user);
        // 更新后删除缓存（Cache-Aside 模式）
        try {
            evictCache(user.getId());
        } catch (Exception e) {
            log.warn("Failed to evict cache after update: {}", e.getMessage());
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        return delegate.existsByEmail(email);
    }

    @Override
    public boolean existsByUserName(String userName) {
        return delegate.existsByUserName(userName);
    }

    @Override
    public boolean existsById(Long userId) {
        // 先检查缓存
        String key = UserRedisKeys.userDetail(userId);
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return !CacheConstants.NULL_VALUE.equals(cached);
            }
        } catch (Exception e) {
            log.warn("Cache lookup failed for existsById: {}", e.getMessage());
        }
        // 缓存未命中，查数据库
        return delegate.existsById(userId);
    }

    /**
     * 缓存用户
     */
    private void cacheUser(String key, User user) {
        if (user != null) {
            // 添加随机抖动防止缓存雪崩
            long ttlWithJitter = cacheProperties.getTtl().getEntityDetail() + randomJitter();
            redisTemplate.opsForValue().set(key, user, ttlWithJitter, TimeUnit.SECONDS);
            log.debug("Cached user: key={}, ttl={}s", key, ttlWithJitter);
        } else {
            // 缓存空值防止缓存穿透
            redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE, 
                    cacheProperties.getTtl().getNullValue(), TimeUnit.SECONDS);
            log.debug("Cached null value: key={}, ttl={}s", key, cacheProperties.getTtl().getNullValue());
        }
    }

    /**
     * 删除缓存
     */
    public void evictCache(Long userId) {
        String key = UserRedisKeys.userDetail(userId);
        redisTemplate.delete(key);
        log.debug("Evicted cache: key={}", key);
    }

    /**
     * 生成随机抖动值
     */
    private int randomJitter() {
        return ThreadLocalRandom.current().nextInt(0, CacheConstants.MAX_JITTER_SECONDS);
    }

    /**
     * 预热用户缓存
     *
     * @param userId 用户ID
     */
    public void warmUpCache(Long userId) {
        try {
            User user = delegate.findById(userId).orElse(null);
            if (user != null) {
                cacheUser(UserRedisKeys.userDetail(userId), user);
                log.debug("Warmed up cache for user: {}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to warm up cache for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * 批量预热用户缓存
     *
     * @param userIds 用户ID列表
     */
    public void warmUpCacheBatch(Iterable<Long> userIds) {
        for (Long userId : userIds) {
            warmUpCache(userId);
        }
    }

    @Override
    public List<User> findAll() {
        // 查询所有用户不走缓存，直接查数据库
        return delegate.findAll();
    }

    @Override
    public List<User> findByConditions(String keyword, String status, int offset, int limit) {
        // 管理查询不走缓存，直接查数据库
        return delegate.findByConditions(keyword, status, offset, limit);
    }

    @Override
    public long countByConditions(String keyword, String status) {
        // 管理查询统计不走缓存，直接查数据库
        return delegate.countByConditions(keyword, status);
    }
}
