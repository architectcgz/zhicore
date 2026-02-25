package com.zhicore.content.application.port.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;

/**
 * 缓存仓储端口接口
 *
 * 说明：
 * - 定义缓存读写/失效的最小契约，由基础设施层实现（例如 Redis）；
 * - 不在此接口内绑定具体业务策略（例如穿透保护、互斥锁、降级 TTL 等）；
 * - 业务侧缓存策略建议通过应用层 Decorator/Query 组合实现。
 */
public interface CacheRepository {
    
    /**
     * 获取缓存值
     * 
     * @param key 缓存键
     * @param type 值类型
     * @param <T> 泛型类型
     * @return 缓存三态结果（MISS/NULL/HIT）
     */
    <T> CacheResult<T> get(String key, Class<T> type);

    /**
     * 获取缓存值（泛型类型）
     *
     * @param key 缓存键
     * @param typeRef 泛型类型引用
     * @param <T> 泛型类型
     * @return 缓存三态结果（MISS/NULL/HIT）
     */
    <T> CacheResult<T> get(String key, TypeReference<T> typeRef);
    
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
     * 注意：pattern 失效通常意味着“批量删除”，应优先精确删除；若必须使用，需考虑 Redis 扫描成本。
     *
     * @param pattern 键模式（支持通配符）
     */
    void deletePattern(String pattern);
}
