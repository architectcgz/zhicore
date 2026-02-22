package com.zhicore.content.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章管理视图对象
 *
 * @author ZhiCore Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostManageDTO {
    
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
