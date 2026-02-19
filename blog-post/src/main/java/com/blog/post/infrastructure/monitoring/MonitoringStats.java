package com.blog.post.infrastructure.monitoring;

import lombok.Builder;
import lombok.Data;

/**
 * 监控统计信息
 */
@Data
@Builder
public class MonitoringStats {
    
    /**
     * PostgreSQL 平均查询时间（毫秒）
     */
    private double postgresAvgQueryTime;
    
    /**
     * MongoDB 平均查询时间（毫秒）
     */
    private double mongoAvgQueryTime;
    
    /**
     * PostgreSQL 慢查询数量
     */
    private long postgresSlowQueryCount;
    
    /**
     * MongoDB 慢查询数量
     */
    private long mongoSlowQueryCount;
    
    /**
     * 双写成功率（0-1之间）
     */
    private double dualWriteSuccessRate;
    
    /**
     * 双写失败率（0-1之间）
     */
    private double dualWriteFailureRate;
    
    /**
     * 数据不一致数量
     */
    private long dataInconsistencyCount;
}
