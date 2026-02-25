package com.zhicore.admin.domain.model;

/**
 * 审计操作类型枚举
 */
public enum AuditAction {
    /**
     * 禁用用户
     */
    DISABLE_USER,
    
    /**
     * 启用用户
     */
    ENABLE_USER,
    
    /**
     * 删除文章
     */
    DELETE_POST,
    
    /**
     * 删除评论
     */
    DELETE_COMMENT,
    
    /**
     * 处理举报
     */
    HANDLE_REPORT,
    
    /**
     * 分配角色
     */
    ASSIGN_ROLE,
    
    /**
     * 撤销角色
     */
    REVOKE_ROLE,
    
    /**
     * 警告用户
     */
    WARN_USER
}
