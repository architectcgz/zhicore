package com.zhicore.content.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.content.application.port.cache.CacheRepository;
import com.zhicore.content.application.port.cache.CacheResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Redis 缓存仓储实现
 *
 * 设计要点：
 * - 使用 String 存储 JSON 序列化对象（支持泛型类型，避免 Java 序列化兼容性问题）；
 * - 使用结构化载荷区分 NULL/HIT，Key 不存在即为 MISS（缓存三态）；
 * - 兼容历史空值标记 {@code __NULL__}，避免上线切换期出现穿透。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisCacheRepositoryImpl implements CacheRepository {
    
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * 旧空值标记（兼容历史数据）
     */
    private static final String NULL_VALUE_MARKER = "__NULL__";
    
    /**
     * SCAN 删除批次大小（默认 100）
     */
    @Value("${cache.delete-pattern.scan-batch-size:100}")
    private int scanBatchSize;
    
    @Override
    public <T> CacheResult<T> get(String key, Class<T> type) {
        return getInternal(key, node -> objectMapper.treeToValue(node, type));
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
    public <T> CacheResult<T> get(String key, TypeReference<T> typeRef) {
        return getInternal(key, node -> objectMapper.readValue(objectMapper.treeAsTokens(node), typeRef));
    }
    
    @Override
    public void set(String key, Object value, Duration ttl) {
        try {
            String jsonValue;
            
            if (value == null) {
                jsonValue = objectMapper.writeValueAsString(CachePayload.nullPayload());
            } else {
                jsonValue = objectMapper.writeValueAsString(CachePayload.hitPayload(value));
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
                jsonValue = objectMapper.writeValueAsString(CachePayload.nullPayload());
            } else {
                jsonValue = objectMapper.writeValueAsString(CachePayload.hitPayload(value));
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
        try {
            if (pattern == null || pattern.isBlank()) {
                return;
            }

            // 没有通配符时按精确 key 删除，避免走 SCAN
            if (!containsWildcard(pattern)) {
                redisTemplate.delete(pattern);
                return;
            }

            // pattern 删除属于“批量失效”，可能产生额外扫描成本，优先精确删除。
            log.warn("执行 deletePattern（通配符批量失效），建议优先精确删除: pattern={}", pattern);

            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(Math.max(1, scanBatchSize))
                    .build();

            Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
                    (RedisCallback<Cursor<byte[]>>) connection -> connection.scan(options)
            );

            if (cursor == null) {
                return;
            }

            List<String> batch = new ArrayList<>(Math.max(10, scanBatchSize));
            long deleted = 0;
            try (cursor) {
                while (cursor.hasNext()) {
                    batch.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    if (batch.size() >= scanBatchSize) {
                        deleted += deleteBatch(batch);
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) {
                deleted += deleteBatch(batch);
            }

            log.info("Deleted cache by pattern via SCAN: pattern={}, deleted={}", pattern, deleted);
        } catch (Exception e) {
            log.error("Failed to delete cache by pattern: pattern={}", pattern, e);
        }
    }

    private long deleteBatch(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        try {
            Long deleted = redisTemplate.delete(Set.copyOf(keys));
            return deleted != null ? deleted : 0;
        } catch (Exception e) {
            log.warn("Failed to delete cache batch: size={}, error={}", keys.size(), e.getMessage());
            return 0;
        }
    }

    private boolean containsWildcard(String pattern) {
        return pattern.contains("*") || pattern.contains("?") || pattern.contains("[");
    }

    private <T> CacheResult<T> getInternal(String key, ValueReader<T> reader) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return CacheResult.miss();
            }

            if (NULL_VALUE_MARKER.equals(value)) {
                return CacheResult.nullValue();
            }

            JsonNode root = objectMapper.readTree(value);
            if (root != null && root.isObject() && root.has("type")) {
                String type = root.get("type").asText();
                if ("NULL".equalsIgnoreCase(type)) {
                    return CacheResult.nullValue();
                }
                if ("HIT".equalsIgnoreCase(type)) {
                    JsonNode payloadNode = root.get("value");
                    if (payloadNode == null || payloadNode.isNull()) {
                        return CacheResult.nullValue();
                    }
                    return CacheResult.hit(reader.read(payloadNode));
                }
            }

            // 兼容旧格式：直接存 value 的 JSON
            return CacheResult.hit(reader.read(root));

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cache value: key={}", key, e);
            redisTemplate.delete(key);
            return CacheResult.miss();
        } catch (Exception e) {
            log.error("Failed to get cache: key={}", key, e);
            return CacheResult.miss();
        }
    }

    @FunctionalInterface
    private interface ValueReader<T> {
        T read(JsonNode node) throws Exception;
    }
}
