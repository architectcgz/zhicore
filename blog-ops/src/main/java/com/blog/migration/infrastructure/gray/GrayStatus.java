package com.blog.migration.infrastructure.gray;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 灰度状态
 */
@Data
@Builder
public class GrayStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前灰度阶段
     */
    private GrayPhase phase;

    /**
     * 当前流量比例
     */
    private int currentRatio;

    /**
     * 开始时间
     */
    private long startTime;

    /**
     * 最后更新时间
     */
    private long lastUpdateTime;

    /**
     * 灰度请求总数
     */
    private long totalRequests;

    /**
     * 灰度错误数
     */
    private long errorCount;

    /**
     * 平均延迟（毫秒）
     */
    private double avgLatencyMs;
}
