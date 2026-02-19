package com.blog.comment.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论管理视图对象
 *
 * @author Blog Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentManageDTO {
    
    /**
     * 评论ID
     */
    private Long id;
    
    /**
     * 文章ID
     */
    private Long postId;
    
    /**
     * 文章标题
     */
    private String postTitle;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户名称
     */
    private String userName;
    
    /**
     * 评论内容
     */
    private String content;
    
    /**
     * 点赞数
     */
    private int likeCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
