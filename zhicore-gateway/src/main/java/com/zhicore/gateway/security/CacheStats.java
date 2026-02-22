package com.zhicore.gateway.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Token 验证缓存统计信息
 * 
 * @author ZhiCore Team
 */
@Data
@Builder
public class CacheStats {
    /**
     * 缓存命中次数
     */
    private long hitCount;
    
    /**
     * 缓存未命中次数
     */
    private long missCount;
    
    /**
     * 缓存命中率
     */
    private double hitRate;
    
    /**
     * 缓存淘汰次数
     */
    private long evictionCount;
    
    /**
     * 当前缓存大小
     */
    private long size;
    
    /**
     * 用于 Jackson 反序列化的构造函数
     */
    @JsonCreator
    public CacheStats(
            @JsonProperty("hitCount") long hitCount,
            @JsonProperty("missCount") long missCount,
            @JsonProperty("hitRate") double hitRate,
            @JsonProperty("evictionCount") long evictionCount,
            @JsonProperty("size") long size) {
        this.hitCount = hitCount;
        this.missCount = missCount;
        this.hitRate = hitRate;
        this.evictionCount = evictionCount;
        this.size = size;
    }
}
