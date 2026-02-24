package com.zhicore.content.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.content.application.port.cache.CacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Redis 缓存仓储实现
 * 
 * 实现 CacheRepository 端口接口，提供基于 Redis 的缓存能力。
 * 使用 String 存储 JSON 序列化的对象，支持泛型类型。
 * 实现空值缓存机制，使用 "__NULL__" 标记防止缓存穿透。
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisCacheRepositoryImpl implements CacheRepository {
    
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * 空值标记，用于缓存空结果防止缓存穿透
     */
    private static final String NULL_VALUE_MARKER = "__NULL__";
    
    /**
     * 是否启用 deletePattern 功能（生产环境应禁用）
     */
    @Value("${cache.delete-pattern.enabled:false}")
    private boolean deletePatternEnabled;
    
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            
            if (value == null) {
                return Optional.empty();
            }
            
            // 检查是否是空值标记
            if (NULL_VALUE_MARKER.equals(value)) {
                return Optional.empty();
            }
            
            // 反序列化对象
            T result = objectMapper.readValue(value, type);
            return Optional.of(result);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cache value: key={}, type={}", key, type.getName(), e);
            // 反序列化失败，删除损坏的缓存
            redisTemplate.delete(key);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get cache: key={}", key, e);
            return Optional.empty();
        }
    }
    
    /**
     * 获取泛型类型的缓存值
     * 
     * 用于处理 List<T>、Map<K,V> 等泛型类型
     * 
     * @param key 缓存键
     * @param typeRef 类型引用，例如 new TypeReference<List<PostListItemView>>() {}
     * @param <T> 返回类型
     * @return 缓存值
     */
    @Override
    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            
            if (value == null) {
                return Optional.empty();
            }
            
            // 检查是否是空值标记
            if (NULL_VALUE_MARKER.equals(value)) {
                return Optional.empty();
            }
            
            // 反序列化泛型对象
            T result = objectMapper.readValue(value, typeRef);
            return Optional.of(result);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cache value: key={}, typeRef={}", key, typeRef.getType(), e);
            // 反序列化失败，删除损坏的缓存
            redisTemplate.delete(key);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get cache: key={}", key, e);
            return Optional.empty();
        }
    }
    
    @Override
    public void set(String key, Object value, Duration ttl) {
        try {
            String jsonValue;
            
            if (value == null) {
                // 空值使用标记
                jsonValue = NULL_VALUE_MARKER;
            } else {
                // 序列化对象
                jsonValue = objectMapper.writeValueAsString(value);
            }
            
            redisTemplate.opsForValue().set(key, jsonValue, ttl);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cache value: key={}, value={}", key, value, e);
        } catch (Exception e) {
            log.error("Failed to set cache: key={}, ttl={}", key, ttl, e);
        }
    }
    
    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        try {
            String jsonValue;
            
            if (value == null) {
                jsonValue = NULL_VALUE_MARKER;
            } else {
                jsonValue = objectMapper.writeValueAsString(value);
            }
            
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, jsonValue, ttl);
            return Boolean.TRUE.equals(result);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cache value: key={}, value={}", key, value, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to setIfAbsent cache: key={}, ttl={}", key, ttl, e);
            return false;
        }
    }
    
    @Override
    public void delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        
        try {
            redisTemplate.delete(Set.of(keys));
        } catch (Exception e) {
            log.error("Failed to delete cache: keys={}", (Object) keys, e);
        }
    }
    
    @Override
    public void deletePattern(String pattern) {
        if (!deletePatternEnabled) {
            log.warn("deletePattern is disabled in production environment: pattern={}", pattern);
            return;
        }
        
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Deleted cache by pattern: pattern={}, count={}", pattern, keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to delete cache by pattern: pattern={}", pattern, e);
        }
    }
}
