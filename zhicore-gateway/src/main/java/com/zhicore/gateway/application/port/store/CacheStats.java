package com.zhicore.gateway.application.port.store;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Token 验证缓存统计信息。
 */
@Data
@Builder
public class CacheStats {

    private long hitCount;
    private long missCount;
    private double hitRate;
    private long evictionCount;
    private long size;

    @JsonCreator
    public CacheStats(@JsonProperty("hitCount") long hitCount,
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
