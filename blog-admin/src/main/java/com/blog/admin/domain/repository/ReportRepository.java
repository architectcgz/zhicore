package com.blog.admin.domain.repository;

import com.blog.admin.domain.model.Report;
import com.blog.admin.domain.model.ReportStatus;
import com.blog.common.result.PageResult;

import java.util.Optional;

/**
 * 举报仓储接口
 */
public interface ReportRepository {
    
    /**
     * 根据ID查询举报
     *
     * @param id 举报ID
     * @return 举报
     */
    Optional<Report> findById(Long id);
    
    /**
     * 更新举报
     *
     * @param report 举报
     */
    void update(Report report);
    
    /**
     * 分页查询待处理举报
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    PageResult<Report> findPendingReports(int page, int size);
    
    /**
     * 按状态分页查询举报
     *
     * @param status 状态
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    PageResult<Report> findByStatus(ReportStatus status, int page, int size);
}
