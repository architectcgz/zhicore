package com.zhicore.admin.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhicore.admin.domain.model.AuditAction;
import com.zhicore.admin.domain.model.AuditLog;
import com.zhicore.admin.domain.repository.AuditLogRepository;
import com.zhicore.admin.infrastructure.repository.mapper.AuditLogMapper;
import com.zhicore.admin.infrastructure.repository.po.AuditLogPO;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 审计日志仓储实现
 */
@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {
    
    private final AuditLogMapper auditLogMapper;
    
    @Override
    public void save(AuditLog auditLog) {
        AuditLogPO po = toPO(auditLog);
        auditLogMapper.insert(po);
    }
    
    @Override
    public Optional<AuditLog> findById(String id) {
        AuditLogPO po = auditLogMapper.selectById(id);
        return Optional.ofNullable(po).map(this::toDomain);
    }
    
    @Override
    public PageResult<AuditLog> findByOperatorId(String operatorId, int page, int size) {
        Page<AuditLogPO> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<AuditLogPO> wrapper = new LambdaQueryWrapper<AuditLogPO>()
                .eq(AuditLogPO::getOperatorId, operatorId)
                .orderByDesc(AuditLogPO::getCreatedAt);
        
        Page<AuditLogPO> result = auditLogMapper.selectPage(pageParam, wrapper);
        
        List<AuditLog> logs = result.getRecords().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        
        return PageResult.of(page, size, result.getTotal(), logs);
    }
    
    @Override
    public List<AuditLog> findByTarget(String targetType, String targetId) {
        LambdaQueryWrapper<AuditLogPO> wrapper = new LambdaQueryWrapper<AuditLogPO>()
                .eq(AuditLogPO::getTargetType, targetType)
                .eq(AuditLogPO::getTargetId, targetId)
                .orderByDesc(AuditLogPO::getCreatedAt);
        
        return auditLogMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }
    
    private AuditLogPO toPO(AuditLog auditLog) {
        AuditLogPO po = new AuditLogPO();
        po.setId(auditLog.getId());
        po.setOperatorId(auditLog.getOperatorId());
        po.setAction(auditLog.getAction().name());
        po.setTargetType(auditLog.getTargetType());
        po.setTargetId(auditLog.getTargetId());
        po.setReason(auditLog.getReason());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(auditLog.getCreatedAt()));
        return po;
    }
    
    private AuditLog toDomain(AuditLogPO po) {
        return AuditLog.reconstitute(
                po.getId(),
                po.getOperatorId(),
                AuditAction.valueOf(po.getAction()),
                po.getTargetType(),
                po.getTargetId(),
                po.getReason(),
                DateTimeUtils.toLocalDateTime(po.getCreatedAt())
        );
    }
}
