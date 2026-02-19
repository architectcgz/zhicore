package com.blog.common.cache;

import com.blog.common.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存辅助工具类
 * 
 * 提供缓存穿透、雪崩、击穿防护功能
 *
 * @author Blog Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheHelper {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;

    /**
     * 获取缓存，带穿透防护（空值缓存）
     *
     * @param key 缓存 Key
     * @param type 返回类型
     * @param loader 数据加载器
     * @param ttlSeconds TTL（秒）
     * @param <T> 返回类型
     * @return 缓存值或数据库值
     */
    @SuppressWarnings("unchecked")
    public <T> T getWithPenetrationProtection(String key, Class<T> type, 
                                               Supplier<T> loader, long ttlSeconds) {
        // 1. 查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        
        // 2. 命中缓存
        if (cached != null) {
            // 空值标记
            if (CacheConstants.NULL_VALUE.equals(cached)) {
                return null;
            }
            return (T) cached;
        }
        
        // 3. 查数据库
        T value = loader.get();
        
        // 4. 写缓存
        if (value != null) {
            // 添加随机抖动防止缓存雪崩
            long ttlWithJitter = ttlSeconds + randomJitter();
            redisTemplate.opsForValue().set(key, value, ttlWithJitter, TimeUnit.SECONDS);
        } else {
            // 缓存空值防止缓存穿透
            redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE, 
                    CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        
        return value;
    }

    /**
     * 获取缓存，使用默认实体详情 TTL
     */
    public <T> T getEntityDetail(String key, Class<T> type, Supplier<T> loader) {
        return getWithPenetrationProtection(key, type, loader, cacheProperties.getTtl().getEntityDetail());
    }

    /**
     * 获取缓存，使用默认列表 TTL
     */
    public <T> T getList(String key, Class<T> type, Supplier<T> loader) {
        return getWithPenetrationProtection(key, type, loader, cacheProperties.getTtl().getList());
    }

    /**
     * 设置缓存，带随机抖动
     *
     * @param key 缓存 Key
     * @param value 缓存值
     * @param ttlSeconds TTL（秒）
     */
    public void setWithJitter(String key, Object value, long ttlSeconds) {
        if (value == null) {
            redisTemplate.opsForValue().set(key, CacheConstants.NULL_VALUE, 
                    CacheConstants.NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
        } else {
            long ttlWithJitter = ttlSeconds + randomJitter();
            redisTemplate.opsForValue().set(key, value, ttlWithJitter, TimeUnit.SECONDS);
        }
    }

    /**
     * 设置永久缓存（用于统计数据）
     *
     * @param key 缓存 Key
     * @param value 缓存值
     */
    public void setPermanent(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 删除缓存
     *
     * @param key 缓存 Key
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 原子递增
     *
     * @param key 缓存 Key
     * @return 递增后的值
     */
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 原子递减
     *
     * @param key 缓存 Key
     * @return 递减后的值
     */
    public Long decrement(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }

    /**
     * 原子递增指定值
     *
     * @param key 缓存 Key
     * @param delta 增量
     * @return 递增后的值
     */
    public Long incrementBy(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 检查 Key 是否存在
     *
     * @param key 缓存 Key
     * @return 是否存在
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 设置过期时间
     *
     * @param key 缓存 Key
     * @param ttlSeconds TTL（秒）
     */
    public void expire(String key, long ttlSeconds) {
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 获取 TTL
     *
     * @param key 缓存 Key
     * @return TTL（秒），-1 表示永久，-2 表示不存在
     */
    public Long getTtl(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 生成随机抖动值
     *
     * @return 随机抖动秒数
     */
    private int randomJitter() {
        return ThreadLocalRandom.current().nextInt(0, CacheConstants.MAX_JITTER_SECONDS);
    }

    /**
     * 计算带抖动的 Duration
     *
     * @param baseTtl 基础 TTL
     * @return 带抖动的 Duration
     */
    public Duration withJitter(Duration baseTtl) {
        return baseTtl.plusSeconds(randomJitter());
    }
}
