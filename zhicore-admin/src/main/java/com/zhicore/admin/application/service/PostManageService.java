package com.zhicore.admin.application.service;

import com.zhicore.admin.application.dto.PostManageVO;
import com.zhicore.admin.domain.model.AuditAction;
import com.zhicore.admin.domain.model.AuditLog;
import com.zhicore.admin.domain.repository.AuditLogRepository;
import com.zhicore.admin.infrastructure.feign.AdminPostServiceClient;
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
 * 文章管理应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostManageService {
    
    private final AdminPostServiceClient postServiceClient;
    private final AuditLogRepository auditLogRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    
    /**
     * 查询文章列表
     *
     * @param keyword  关键词
     * @param status   状态
     * @param authorId 作者ID
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    public PageResult<PostManageVO> listPosts(String keyword, String status, Long authorId, int page, int size) {
        ApiResponse<PageResult<AdminPostServiceClient.PostManageDTO>> response = 
                postServiceClient.queryPosts(keyword, status, authorId, page, size);
        
        if (!response.isSuccess()) {
            throw new BusinessException(response.getMessage());
        }
        
        PageResult<AdminPostServiceClient.PostManageDTO> result = response.getData();
        List<PostManageVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        
        return PageResult.of(page, size, result.getTotal(), voList);
    }
    
    /**
     * 删除文章
     *
     * @param adminId 管理员ID
     * @param postId  文章ID
     * @param reason  删除原因
     */
    @Transactional
    public void deletePost(Long adminId, Long postId, String reason) {
        log.info("Admin {} deleting post {} with reason: {}", adminId, postId, reason);
        
        // 1. 软删除文章
        ApiResponse<Void> response = postServiceClient.deletePost(postId);
        if (!response.isSuccess()) {
            throw new BusinessException("删除文章失败: " + response.getMessage());
        }
        
        // 2. 记录审计日志
        Long logId = generateId();
        AuditLog auditLog = AuditLog.create(
                logId,
                adminId,
                AuditAction.DELETE_POST,
                "post",
                postId,
                reason
        );
        auditLogRepository.save(auditLog);
        
        log.info("Post {} deleted successfully by admin {}", postId, adminId);
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
    
    private PostManageVO toVO(AdminPostServiceClient.PostManageDTO dto) {
        return PostManageVO.builder()
                .id(dto.id())
                .title(dto.title())
                .authorId(dto.authorId())
                .authorName(dto.authorName())
                .status(dto.status())
                .viewCount(dto.viewCount())
                .likeCount(dto.likeCount())
                .commentCount(dto.commentCount())
                .createdAt(dto.createdAt())
                .publishedAt(dto.publishedAt())
                .build();
    }
}
