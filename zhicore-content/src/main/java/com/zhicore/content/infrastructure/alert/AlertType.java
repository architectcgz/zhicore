package com.zhicore.content.infrastructure.alert;

/**
 * 告警类型枚举
 */
public enum AlertType {
    /**
     * 数据不一致告警
     */
    DATA_INCONSISTENCY("数据不一致", AlertLevel.HIGH),
    
    /**
     * MongoDB 连接失败告警
     */
    MONGODB_CONNECTION_FAILURE("MongoDB连接失败", AlertLevel.CRITICAL),
    
    /**
     * PostgreSQL 连接失败告警
     */
    POSTGRES_CONNECTION_FAILURE("PostgreSQL连接失败", AlertLevel.CRITICAL),
    
    /**
     * 查询性能下降告警
     */
    QUERY_PERFORMANCE_DEGRADATION("查询性能下降", AlertLevel.MEDIUM),
    
    /**
     * 存储空间不足告警
     */
    STORAGE_SPACE_LOW("存储空间不足", AlertLevel.HIGH),
    
    /**
     * 双写失败率过高告警
     */
    DUAL_WRITE_FAILURE_RATE_HIGH("双写失败率过高", AlertLevel.HIGH),
    
    /**
     * 慢查询告警
     */
    SLOW_QUERY("慢查询", AlertLevel.MEDIUM),

    /**
     * Outbox 投递失败告警（重试耗尽后的失败收敛，通常需要人工介入处理）
     */
    OUTBOX_DISPATCH_FAILED("Outbox投递失败", AlertLevel.HIGH),

    /**
     * 正文图片清理失败告警（删除文章时异步清理正文内图片资源失败）
     */
    CONTENT_IMAGE_CLEANUP_FAILED("正文图片清理失败", AlertLevel.MEDIUM);

    private final String description;
    private final AlertLevel level;

    AlertType(String description, AlertLevel level) {
        this.description = description;
        this.level = level;
    }

    public String getDescription() {
        return description;
    }

    public AlertLevel getLevel() {
        return level;
    }
}
