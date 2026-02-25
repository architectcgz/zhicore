package com.zhicore.admin.domain.model;

/**
 * 举报处理动作枚举
 */
public enum ReportHandleAction {
    /**
     * 删除内容
     */
    DELETE_CONTENT,
    
    /**
     * 警告用户
     */
    WARN_USER,
    
    /**
     * 封禁用户
     */
    BAN_USER,
    
    /**
     * 忽略举报
     */
    IGNORE
}
