package com.zhicore.content.application.query.view;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 标签详情视图
 * 
 * 用于查询服务返回完整的标签信息
 * 
 * @author ZhiCore Team
 */
@Data
@Builder
public class TagDetailView {
    
    /**
     * 标签 ID
     */
    private Long id;
    
    /**
     * 标签名称
     */
    private String name;
    
    /**
     * 标签 slug
     */
    private String slug;
    
    /**
     * 标签描述
     */
    private String description;
    
    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
    
    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;
}
