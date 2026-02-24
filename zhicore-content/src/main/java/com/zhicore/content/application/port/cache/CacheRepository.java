package com.zhicore.content.application.port.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Optional;

/**
 * 缓存仓储端口接口
 * 
 * 定义缓存操作的契约，由基础设施层实现（如 Redis）。
 * 提供基础的缓存能力，不包含业务逻辑和缓存策略。
 * 缓存策略由应用层的 Decorator 实现。
 * 
 * @author ZhiCore Team
 */
public interface CacheRepository {
    
    /**
     * 获取缓存值
     * 
     * @param key 缓存键
     * @param type 值类型
     * @param <T> 泛型类型
     * @return 缓存值（可能为空）
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * 获取缓存值（泛型类型）
     *
     * @param key 缓存键
     * @param typeRef 泛型类型引用
     * @param <T> 泛型类型
     * @return 缓存值（可能为空）
     */
    <T> Optional<T> get(String key, TypeReference<T> typeRef);
    
    /**
     * 设置缓存值
     * 
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 过期时间
     */
    void set(String key, Object value, Duration ttl);
    
    /**
     * 仅当键不存在时设置缓存值
     * 
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 过期时间
     * @return 是否设置成功
     */
    boolean setIfAbsent(String key, Object value, Duration ttl);
    
    /**
     * 删除缓存
     * 
     * @param keys 缓存键列表
     */
    void delete(String... keys);
    
    /**
     * 根据模式删除缓存
     * 
     * 注意：此操作在生产环境应谨慎使用，可能影响性能。
     * 建议仅在开发环境启用，生产环境通过配置禁用。
     * 
     * @param pattern 键模式（支持通配符）
     */
    void deletePattern(String pattern);
}
