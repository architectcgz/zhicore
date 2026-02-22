package com.zhicore.admin.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhicore.admin.domain.model.Report;
import com.zhicore.admin.domain.model.ReportHandleAction;
import com.zhicore.admin.domain.model.ReportStatus;
import com.zhicore.admin.domain.repository.ReportRepository;
import com.zhicore.admin.infrastructure.repository.mapper.ReportMapper;
import com.zhicore.admin.infrastructure.repository.po.ReportPO;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 举报仓储实现
 */
@Repository
@RequiredArgsConstructor
public class ReportRepositoryImpl implements ReportRepository {
    
    private final ReportMapper reportMapper;
    
    @Override
    public Optional<Report> findById(Long id) {
        ReportPO po = reportMapper.selectById(id);
        return Optional.ofNullable(po).map(this::toDomain);
    }
    
    @Override
    public void update(Report report) {
        LambdaUpdateWrapper<ReportPO> wrapper = new LambdaUpdateWrapper<ReportPO>()
                .eq(ReportPO::getId, report.getId())
                .set(ReportPO::getStatus, report.getStatus().name())
                .set(ReportPO::getHandlerId, report.getHandlerId())
                .set(ReportPO::getHandleAction, report.getHandleAction() != null ? report.getHandleAction().name() : null)
                .set(ReportPO::getHandleRemark, report.getHandleRemark())
                .set(ReportPO::getHandledAt, DateTimeUtils.toOffsetDateTime(report.getHandledAt()));
        
        reportMapper.update(null, wrapper);
    }
    
    @Override
    public PageResult<Report> findPendingReports(int page, int size) {
        return findByStatus(ReportStatus.PENDING, page, size);
    }
    
    @Override
    public PageResult<Report> findByStatus(ReportStatus status, int page, int size) {
        Page<ReportPO> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<ReportPO> wrapper = new LambdaQueryWrapper<ReportPO>()
                .eq(ReportPO::getStatus, status.name())
                .orderByDesc(ReportPO::getCreatedAt);
        
        Page<ReportPO> result = reportMapper.selectPage(pageParam, wrapper);
        
        List<Report> reports = result.getRecords().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        
        return PageResult.of(page, size, result.getTotal(), reports);
    }
    
    private Report toDomain(ReportPO po) {
        return Report.reconstitute(
                po.getId(),
                po.getReporterId(),
                po.getReportedUserId(),
                po.getTargetType(),
                po.getTargetId(),
                po.getReason(),
                ReportStatus.valueOf(po.getStatus()),
                po.getHandlerId(),
                po.getHandleAction() != null ? ReportHandleAction.valueOf(po.getHandleAction()) : null,
                po.getHandleRemark(),
                DateTimeUtils.toLocalDateTime(po.getHandledAt()),
                DateTimeUtils.toLocalDateTime(po.getCreatedAt())
        );
    }
}
