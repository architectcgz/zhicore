package com.zhicore.content.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 归档策略配置属性
 * 从 application.yml 读取归档相关配置
 * 
 * Requirements: 12.2
 */
@Data
@Component
@ConfigurationProperties(prefix = "post.archive")
public class ArchiveProperties {

    /**
     * 归档时间阈值（天数）
     * 文章超过此天数未更新将被标记为冷数据候选
     * 默认: 180 天（6个月）
     */
    private int thresholdDays = 180;

    /**
     * 归档任务执行频率（cron 表达式）
     * 默认: 每天凌晨 3 点执行
     */
    private String scheduleCron = "0 0 3 * * ?";

    /**
     * 每批次归档的最大文章数
     * 默认: 100
     */
    private int batchSize = 100;

    /**
     * 是否启用自动归档
     * 默认: true
     */
    private boolean enabled = true;

    /**
     * 归档失败重试次数
     * 默认: 3
     */
    private int retryCount = 3;

    /**
     * 归档后是否保留 PostgreSQL 中的内容字段
     * 默认: false（删除以节省空间）
     */
    private boolean keepContentInPostgres = false;

    /**
     * 归档前是否需要备份
     * 默认: true
     */
    private boolean backupBeforeArchive = true;

    /**
     * 归档日志保留天数
     * 默认: 90 天
     */
    private int logRetentionDays = 90;
}
