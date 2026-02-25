package com.zhicore.admin.application.service;

import com.zhicore.admin.application.dto.CommentManageVO;
import com.zhicore.admin.domain.model.AuditAction;
import com.zhicore.admin.domain.model.AuditLog;
import com.zhicore.admin.domain.repository.AuditLogRepository;
import com.zhicore.admin.infrastructure.feign.AdminCommentServiceClient;
import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 评论管理应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentManageService {
    
    private final AdminCommentServiceClient commentServiceClient;
    private final AuditLogRepository auditLogRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    
    /**
     * 查询评论列表
     *
     * @param keyword 关键词
     * @param postId  文章ID
     * @param userId  用户ID
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    public PageResult<CommentManageVO> listComments(String keyword, Long postId, Long userId, int page, int size) {
        ApiResponse<PageResult<AdminCommentServiceClient.CommentManageDTO>> response = 
                commentServiceClient.queryComments(keyword, postId, userId, page, size);
        
        if (!response.isSuccess()) {
            throw new BusinessException(response.getMessage());
        }
        
        PageResult<AdminCommentServiceClient.CommentManageDTO> result = response.getData();
        List<CommentManageVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        
        return PageResult.of(page, size, result.getTotal(), voList);
    }
    
    /**
     * 删除评论
     *
     * @param adminId   管理员ID
     * @param commentId 评论ID
     * @param reason    删除原因
     */
    @Transactional
    public void deleteComment(Long adminId, Long commentId, String reason) {
        log.info("Admin {} deleting comment {} with reason: {}", adminId, commentId, reason);
        
        // 1. 软删除评论
        ApiResponse<Void> response = commentServiceClient.deleteComment(commentId);
        if (!response.isSuccess()) {
            throw new BusinessException("删除评论失败: " + response.getMessage());
        }
        
        // 2. 记录审计日志
        Long logId = generateId();
        AuditLog auditLog = AuditLog.create(
                logId,
                adminId,
                AuditAction.DELETE_COMMENT,
                "comment",
                commentId,
                reason
        );
        auditLogRepository.save(auditLog);
        
        log.info("Comment {} deleted successfully by admin {}", commentId, adminId);
    }
    
    private Long generateId() {
        ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
        if (!response.isSuccess()) {
            log.error("生成ID失败: code={}, message={}", response.getCode(), response.getMessage());
            throw new BusinessException("生成ID失败: " + response.getMessage());
        }
        
        Long id = response.getData();
        if (id == null || id <= 0) {
            log.error("生成的ID无效: {}", id);
            throw new BusinessException("生成的ID无效");
        }
        
        return id;
    }
    
    private CommentManageVO toVO(AdminCommentServiceClient.CommentManageDTO dto) {
        return CommentManageVO.builder()
                .id(dto.id())
                .postId(dto.postId())
                .postTitle(dto.postTitle())
                .userId(dto.userId())
                .userName(dto.userName())
                .content(dto.content())
                .likeCount(dto.likeCount())
                .createdAt(dto.createdAt())
                .build();
    }
}
