package com.zhicore.admin.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 评论管理视图对象
 */
@Data
@Builder
public class CommentManageVO {
    
    /**
     * 评论ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    
    /**
     * 文章ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long postId;
    
    /**
     * 文章标题
     */
    private String postTitle;
    
    /**
     * 用户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
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
    private OffsetDateTime createdAt;
}
