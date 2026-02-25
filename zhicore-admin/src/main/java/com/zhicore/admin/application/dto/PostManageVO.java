package com.zhicore.admin.application.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章管理视图对象
 */
@Data
@Builder
public class PostManageVO {
    
    /**
     * 文章ID
     */
    private Long id;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 作者ID
     */
    private Long authorId;
    
    /**
     * 作者名称
     */
    private String authorName;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 浏览数
     */
    private int viewCount;
    
    /**
     * 点赞数
     */
    private int likeCount;
    
    /**
     * 评论数
     */
    private int commentCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;
}
