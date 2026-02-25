package com.zhicore.admin.domain.repository;

import com.zhicore.admin.domain.model.AuditLog;
import com.zhicore.common.result.PageResult;

import java.util.List;
import java.util.Optional;

/**
 * 审计日志仓储接口
 */
public interface AuditLogRepository {
    
    /**
     * 保存审计日志
     *
     * @param auditLog 审计日志
     */
    void save(AuditLog auditLog);
    
    /**
     * 根据ID查询审计日志
     *
     * @param id 日志ID
     * @return 审计日志
     */
    Optional<AuditLog> findById(String id);
    
    /**
     * 根据操作者ID分页查询审计日志
     *
     * @param operatorId 操作者ID
     * @param page       页码
     * @param size       每页大小
     * @return 分页结果
     */
    PageResult<AuditLog> findByOperatorId(String operatorId, int page, int size);
    
    /**
     * 根据目标类型和目标ID查询审计日志
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return 审计日志列表
     */
    List<AuditLog> findByTarget(String targetType, String targetId);
}
