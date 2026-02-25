package com.zhicore.admin.domain.model;

/**
 * 举报状态枚举
 */
public enum ReportStatus {
    /**
     * 待处理
     */
    PENDING,
    
    /**
     * 已处理
     */
    PROCESSED,
    
    /**
     * 已忽略
     */
    IGNORED
}
