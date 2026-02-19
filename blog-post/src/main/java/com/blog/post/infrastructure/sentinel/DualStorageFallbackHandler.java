package com.blog.post.infrastructure.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.blog.post.infrastructure.alert.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 双写操作降级处理器
 * 当 Sentinel 触发降级时，提供降级逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DualStorageFallbackHandler {

    private final AlertService alertService;

    /**
     * MongoDB 连接失败降级处理
     * 当 MongoDB 不可用时，仅使用 PostgreSQL
     *
     * @param postId 文章ID
     * @param ex 阻塞异常
     * @return null 表示降级
     */
    public Object handleMongoConnectionFailure(String postId, BlockException ex) {
        log.warn("[Sentinel Fallback] MongoDB connection failure detected for postId: {}, degrading to PostgreSQL only", 
                postId);
        
        // 触发告警
        alertService.alertMongoConnectionFailure(
                String.format("MongoDB connection blocked by Sentinel for postId: %s, reason: %s", 
                        postId, ex.getClass().getSimpleName())
        );
        
        // 返回 null 表示降级，调用方需要处理降级逻辑
        return null;
    }

    /**
     * 双写操作失败降级处理
     * 当双写失败率过高时触发
     *
     * @param ex 阻塞异常
     */
    public void handleDualWriteFailure(BlockException ex) {
        log.error("[Sentinel Fallback] Dual write operation blocked due to high failure rate: {}", 
                ex.getClass().getSimpleName());
        
        // 触发告警
        alertService.alertDualWriteFailureRateHigh(
                0.0, // 实际失败率由监控系统计算
                0.05 // 阈值
        );
    }

    /**
     * 查询性能下降降级处理
     * 当查询响应时间过长时触发
     *
     * @param database 数据库类型
     * @param ex 阻塞异常
     */
    public void handleQueryPerformanceDegradation(String database, BlockException ex) {
        log.warn("[Sentinel Fallback] Query performance degradation detected for {}: {}", 
                database, ex.getClass().getSimpleName());
        
        // 触发告警
        alertService.alertQueryPerformanceDegradation(
                database,
                0.0, // 实际响应时间由监控系统计算
                500.0 // 阈值
        );
    }
}
