package com.zhicore.content.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 版本控制配置属性
 * 从 application.yml 读取版本历史相关配置
 * 
 * Requirements: 12.3
 */
@Data
@Component
@ConfigurationProperties(prefix = "post.version")
public class VersionProperties {

    /**
     * 每篇文章保留的最大版本数
     * 超过此数量将自动清理最旧的版本
     * 默认: 50
     */
    private int maxVersionsPerPost = 50;

    /**
     * 版本清理策略
     * - auto: 自动清理（超过最大版本数时）
     * - manual: 手动清理
     * 默认: auto
     */
    private String cleanupStrategy = "auto";

    /**
     * 版本清理任务执行频率（cron 表达式）
     * 默认: 每天凌晨 4 点执行
     */
    private String cleanupScheduleCron = "0 0 4 * * ?";

    /**
     * 是否启用版本控制
     * 默认: true
     */
    private boolean enabled = true;

    /**
     * 版本内容压缩
     * 是否对版本内容进行压缩存储以节省空间
     * 默认: false
     */
    private boolean compressionEnabled = false;

    /**
     * 版本保留最小天数
     * 即使超过最大版本数，也至少保留此天数内的版本
     * 默认: 30 天
     */
    private int minRetentionDays = 30;

    /**
     * 是否记录版本变更详情
     * 默认: true
     */
    private boolean trackChanges = true;

    /**
     * 版本快照包含的字段
     * - full: 完整内容
     * - content-only: 仅内容
     * 默认: full
     */
    private String snapshotMode = "full";
}
