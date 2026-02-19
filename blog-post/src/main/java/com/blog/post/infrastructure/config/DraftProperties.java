package com.blog.post.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 草稿配置属性
 * 从 application.yml 读取草稿自动保存相关配置
 * 
 * Requirements: 12.4
 */
@Data
@Component
@ConfigurationProperties(prefix = "post.draft")
public class DraftProperties {

    /**
     * 自动保存间隔（秒）
     * 编辑器每隔此时间自动保存草稿
     * 默认: 30 秒
     */
    private int autoSaveIntervalSeconds = 30;

    /**
     * 草稿过期时间（天数）
     * 超过此时间未更新的草稿将被自动清理
     * 默认: 30 天
     */
    private int expirationDays = 30;

    /**
     * 草稿清理任务执行频率（cron 表达式）
     * 默认: 每天凌晨 2 点执行
     */
    private String cleanupScheduleCron = "0 0 2 * * ?";

    /**
     * 是否启用自动保存
     * 默认: true
     */
    private boolean autoSaveEnabled = true;

    /**
     * 每个用户最多保留的草稿数
     * 超过此数量将删除最旧的草稿
     * 默认: 100
     */
    private int maxDraftsPerUser = 100;

    /**
     * 草稿保存失败是否中断编辑
     * 默认: false（仅显示警告）
     */
    private boolean failOnSaveError = false;

    /**
     * 是否启用草稿缓存
     * 将草稿缓存到 Redis 以提升性能
     * 默认: true
     */
    private boolean cacheEnabled = true;

    /**
     * 草稿缓存过期时间（秒）
     * 默认: 3600 秒（1小时）
     */
    private int cacheTtlSeconds = 3600;

    /**
     * 草稿恢复提示阈值（秒）
     * 草稿保存时间与文章更新时间差超过此值时提示恢复
     * 默认: 60 秒
     */
    private int restorePromptThresholdSeconds = 60;

    /**
     * 是否在发布时自动删除草稿
     * 默认: true
     */
    private boolean deleteOnPublish = true;
}
