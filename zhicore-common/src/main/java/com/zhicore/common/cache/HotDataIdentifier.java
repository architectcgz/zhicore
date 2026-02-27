package com.zhicore.common.cache;

import com.zhicore.common.config.CacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 热点数据识别器
 * 
 * 使用 Redis 计数器统计访问频率，识别热点数据
 * 只对热点数据使用分布式锁，提升系统性能
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class HotDataIdentifier {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;

    /**
     * 热点数据计数器前缀
     * Key: hotdata:counter:{entityType}:{entityId}
     */
    private static String counterPrefix() {
        return CacheConstants.withNamespace("hotdata") + ":counter";
    }

    /**
     * 手动标记热点数据前缀
     * Key: hotdata:manual:{entityType}:{entityId}
     */
    private static String manualPrefix() {
        return CacheConstants.withNamespace("hotdata") + ":manual";
    }

    /**
     * 计数器过期时间（1小时）
     */
    private static final long COUNTER_TTL_HOURS = 1;

    /**
     * 手动标记过期时间（24小时）
     */
    private static final long MANUAL_TTL_HOURS = 24;

    public HotDataIdentifier(RedisTemplate<String, Object> redisTemplate,
                             CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
    }

    /**
     * 记录访问
     * 
     * 每次访问实体时调用此方法，增加访问计数
     * 计数器有效期为1小时，过期后自动清零
     * 
     * @param entityType 实体类型（post, user, comment）
     * @param entityId 实体ID
     */
    public void recordAccess(String entityType, Long entityId) {
        if (!cacheProperties.getHotData().isEnabled()) {
            return;
        }

        if (entityType == null || entityId == null) {
            log.warn("Invalid parameters for recordAccess: entityType={}, entityId={}", entityType, entityId);
            return;
        }

        try {
            String key = buildCounterKey(entityType, entityId);
            
            // 增加计数
            redisTemplate.opsForValue().increment(key);
            
            // 设置过期时间（1小时）
            redisTemplate.expire(key, COUNTER_TTL_HOURS, TimeUnit.HOURS);
            
        } catch (Exception e) {
            // 记录访问失败不应影响业务流程
            log.warn("Failed to record access for {}:{}: {}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * 判断是否为热点数据
     * 
     * 根据访问频率判断是否为热点数据
     * 阈值：1小时内访问次数超过配置的阈值（默认100次）
     * 
     * @param entityType 实体类型（post, user, comment）
     * @param entityId 实体ID
     * @return true 如果是热点数据
     */
    public boolean isHotData(String entityType, Long entityId) {
        if (!cacheProperties.getHotData().isEnabled()) {
            return false;
        }

        if (entityType == null || entityId == null) {
            return false;
        }

        try {
            String key = buildCounterKey(entityType, entityId);
            Object count = redisTemplate.opsForValue().get(key);
            
            if (count == null) {
                return false;
            }
            
            // 获取配置的阈值
            int threshold = cacheProperties.getHotData().getThreshold();
            int accessCount = ((Number) count).intValue();
            
            return accessCount > threshold;
            
        } catch (Exception e) {
            log.warn("Failed to check hot data for {}:{}: {}", entityType, entityId, e.getMessage());
            // 检查失败时，保守处理：不认为是热点数据
            return false;
        }
    }

    /**
     * 手动标记热点数据
     * 
     * 用于已知的热点数据（如热门文章、热门用户）
     * 手动标记的数据有效期为24小时
     * 
     * @param entityType 实体类型（post, user, comment）
     * @param entityId 实体ID
     */
    public void markAsHot(String entityType, Long entityId) {
        if (entityType == null || entityId == null) {
            log.warn("Invalid parameters for markAsHot: entityType={}, entityId={}", entityType, entityId);
            return;
        }

        try {
            String key = buildManualKey(entityType, entityId);
            
            // 标记为热点数据
            redisTemplate.opsForValue().set(key, "1", MANUAL_TTL_HOURS, TimeUnit.HOURS);
            
            log.info("Manually marked {}:{} as hot data", entityType, entityId);
            
        } catch (Exception e) {
            log.error("Failed to mark {}:{} as hot data: {}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * 检查是否被手动标记为热点
     * 
     * @param entityType 实体类型（post, user, comment）
     * @param entityId 实体ID
     * @return true 如果被手动标记为热点
     */
    public boolean isManuallyMarkedAsHot(String entityType, Long entityId) {
        if (entityType == null || entityId == null) {
            return false;
        }

        try {
            String key = buildManualKey(entityType, entityId);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
            
        } catch (Exception e) {
            log.warn("Failed to check manual mark for {}:{}: {}", entityType, entityId, e.getMessage());
            return false;
        }
    }

    /**
     * 取消手动标记
     * 
     * @param entityType 实体类型（post, user, comment）
     * @param entityId 实体ID
     */
    public void unmarkAsHot(String entityType, Long entityId) {
        if (entityType == null || entityId == null) {
            log.warn("Invalid parameters for unmarkAsHot: entityType={}, entityId={}", entityType, entityId);
            return;
        }

        try {
            String key = buildManualKey(entityType, entityId);
            redisTemplate.delete(key);
            
            log.info("Removed hot data mark for {}:{}", entityType, entityId);
            
        } catch (Exception e) {
            log.error("Failed to unmark {}:{} as hot data: {}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * 获取访问计数
     * 
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 访问次数，如果不存在返回0
     */
    public int getAccessCount(String entityType, Long entityId) {
        if (entityType == null || entityId == null) {
            return 0;
        }

        try {
            String key = buildCounterKey(entityType, entityId);
            Object count = redisTemplate.opsForValue().get(key);
            
            if (count == null) {
                return 0;
            }
            
            return ((Number) count).intValue();
            
        } catch (Exception e) {
            log.warn("Failed to get access count for {}:{}: {}", entityType, entityId, e.getMessage());
            return 0;
        }
    }

    /**
     * 重置访问计数
     * 
     * @param entityType 实体类型
     * @param entityId 实体ID
     */
    public void resetAccessCount(String entityType, Long entityId) {
        if (entityType == null || entityId == null) {
            log.warn("Invalid parameters for resetAccessCount: entityType={}, entityId={}", entityType, entityId);
            return;
        }

        try {
            String key = buildCounterKey(entityType, entityId);
            redisTemplate.delete(key);
            
            log.debug("Reset access count for {}:{}", entityType, entityId);
            
        } catch (Exception e) {
            log.warn("Failed to reset access count for {}:{}: {}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * 构建计数器 Key
     * 
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return Redis Key
     */
    private String buildCounterKey(String entityType, Long entityId) {
        return counterPrefix() + CacheConstants.SEPARATOR + entityType + CacheConstants.SEPARATOR + entityId;
    }

    /**
     * 构建手动标记 Key
     * 
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return Redis Key
     */
    private String buildManualKey(String entityType, Long entityId) {
        return manualPrefix() + CacheConstants.SEPARATOR + entityType + CacheConstants.SEPARATOR + entityId;
    }
}
