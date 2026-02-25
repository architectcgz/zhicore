package com.zhicore.common.cache;

import com.zhicore.common.config.CacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存仓储装饰器基类
 * 
 * 实现 Cache-Aside 模式：
 * - 读：先查缓存，未命中再查数据库，然后写缓存
 * - 写：先更新数据库，再删除缓存
 * 
 * 提供缓存穿透、雪崩、击穿防护
 *
 * @param <T> 实体类型
 * @param <ID> ID 类型
 * @author ZhiCore Team
 */
@Slf4j
public abstract class AbstractCachedRepository<T, ID> {

    protected final RedisTemplate<String, Object> redisTemplate;
    protected final CacheProperties cacheProperties;

    protected AbstractCachedRepository(RedisTemplate<String, Object> redisTemplate,
                                       CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
    }

    /**
     * 获取缓存 Key
     *
     * @param id 实体 ID
     * @return 缓存 Key
     */
    protected abstract String getCacheKey(ID id);

    /**
     * 从数据库加载实体
     *
     * @param id 实体 ID
     * @return 实体对象
     */
    protected abstract T loadFromDatabase(ID id);

    /**
     * 获取缓存 TTL（秒）
     *
     * @return TTL 秒数
     */
    protected long getCacheTtlSeconds() {
        return cacheProperties.getTtl().getEntityDetail();
    }

    /**
     * 根据 ID 查询实体（带缓存）
     *
     * @param id 实体 ID
     * @return 实体对象
     */
    @SuppressWarnings("unchecked")
    public T findById(ID id) {
        String key = getCacheKey(id);
        
        // 1. 查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        
        // 2. 命中缓存
        if (cached != null) {
            if (CacheConstants.NULL_VALUE.equals(cached)) {
                log.debug("Cache hit (null value): key={}", key);
                return null;
            }
            log.debug("Cache hit: key={}", key);
            return (T) cached;
        }
        
        // 3. 未命中，查数据库
        log.debug("Cache miss: key={}", key);
        T entity = loadFromDatabase(id);
        
        // 4. 写缓存
        cacheEntity(key, entity);
        
        return entity;
    }

    /**
     * 缓存实体
     *
     * @param key 缓存 Key
     * @param entity 实体对象
     */
    protected void cacheEntity(String key, T entity) {
        if (entity != null) {
            // 添加随机抖动防止缓存雪崩
            long ttlWithJitter = getCacheTtlSeconds() + randomJitter();
            redisTemplate.opsForValue().set(key, entity, ttlWithJitter, TimeUnit.SECONDS);
            log.debug("Cached entity: key={}, ttl={}s", key, ttlWithJitter);
        } else {
            // 缓存空值防止缓存穿透
            redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE, 
                    CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Cached null value: key={}", key);
        }
    }

    /**
     * 删除缓存
     *
     * @param id 实体 ID
     */
    public void evictCache(ID id) {
        String key = getCacheKey(id);
        redisTemplate.delete(key);
        log.debug("Evicted cache: key={}", key);
    }

    /**
     * 刷新缓存
     *
     * @param id 实体 ID
     * @return 刷新后的实体
     */
    public T refreshCache(ID id) {
        evictCache(id);
        return findById(id);
    }

    /**
     * 带分布式锁的缓存加载（防止缓存击穿）
     *
     * @param id 实体 ID
     * @param lockTimeoutSeconds 锁超时时间（秒）
     * @return 实体对象
     */
    @SuppressWarnings("unchecked")
    public T findByIdWithLock(ID id, long lockTimeoutSeconds) {
        String key = getCacheKey(id);
        
        // 1. 第一次检查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (CacheConstants.NULL_VALUE.equals(cached)) {
                return null;
            }
            return (T) cached;
        }
        
        // 2. 获取分布式锁
        String lockKey = RedisKeyBuilder.Lock.forEntity(getEntityName(), id);
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", lockTimeoutSeconds, TimeUnit.SECONDS);
        
        if (Boolean.TRUE.equals(locked)) {
            try {
                // 3. 双重检查
                cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    if (CacheConstants.NULL_VALUE.equals(cached)) {
                        return null;
                    }
                    return (T) cached;
                }
                
                // 4. 查数据库并缓存
                T entity = loadFromDatabase(id);
                cacheEntity(key, entity);
                return entity;
            } finally {
                // 5. 释放锁
                redisTemplate.delete(lockKey);
            }
        } else {
            // 获取锁失败，等待后重试
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return findByIdWithLock(id, lockTimeoutSeconds);
        }
    }

    /**
     * 获取实体名称（用于锁 Key）
     *
     * @return 实体名称
     */
    protected abstract String getEntityName();

    /**
     * 生成随机抖动值
     *
     * @return 随机抖动秒数
     */
    protected int randomJitter() {
        return ThreadLocalRandom.current().nextInt(0, CacheConstants.MAX_JITTER_SECONDS);
    }

    /**
     * 使用自定义加载器获取缓存
     *
     * @param key 缓存 Key
     * @param loader 数据加载器
     * @param ttlSeconds TTL（秒）
     * @param <R> 返回类型
     * @return 缓存值或加载的值
     */
    @SuppressWarnings("unchecked")
    protected <R> R getOrLoad(String key, Supplier<R> loader, long ttlSeconds) {
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached != null) {
            if (CacheConstants.NULL_VALUE.equals(cached)) {
                return null;
            }
            return (R) cached;
        }
        
        R value = loader.get();
        
        if (value != null) {
            long ttlWithJitter = ttlSeconds + randomJitter();
            redisTemplate.opsForValue().set(key, value, ttlWithJitter, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE, 
                    CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        
        return value;
    }
}
