package com.zhicore.content.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 一致性检查配置属性
 * 从 application.yml 读取数据一致性检查相关配置
 * 
 * Requirements: 12.5
 */
@Data
@Component
@ConfigurationProperties(prefix = "post.consistency")
public class ConsistencyProperties {

    /**
     * 一致性检查任务执行频率（cron 表达式）
     * 默认: 每天凌晨 2 点执行
     */
    private String checkScheduleCron = "0 0 2 * * ?";

    /**
     * 每批次检查的文章数
     * 默认: 100
     */
    private int batchSize = 100;

    /**
     * 是否启用定时一致性检查
     * 默认: true
     */
    private boolean scheduledCheckEnabled = true;

    /**
     * 数据修复策略
     * - postgres: 以 PostgreSQL 为准
     * - mongodb: 以 MongoDB 为准
     * - manual: 手动修复
     * 默认: postgres
     */
    private String repairStrategy = "postgres";

    /**
     * 是否自动修复不一致数据
     * 默认: false（仅记录，不自动修复）
     */
    private boolean autoRepair = false;

    /**
     * 检查超时时间（秒）
     * 单个文章的一致性检查超时时间
     * 默认: 30 秒
     */
    private int checkTimeoutSeconds = 30;

    /**
     * 不一致数据告警阈值
     * 不一致数据数量超过此值时触发告警
     * 默认: 10
     */
    private int alertThreshold = 10;

    /**
     * 是否记录一致性检查日志
     * 默认: true
     */
    private boolean logEnabled = true;

    /**
     * 检查报告保留天数
     * 默认: 90 天
     */
    private int reportRetentionDays = 90;

    /**
     * 孤儿数据清理
     * 是否自动清理 MongoDB 中的孤儿数据
     * 默认: true
     */
    private boolean cleanOrphanData = true;

    /**
     * 孤儿数据清理任务执行频率（cron 表达式）
     * 默认: 每天凌晨 3 点执行
     */
    private String orphanCleanupScheduleCron = "0 0 3 * * ?";
}
