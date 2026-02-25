package com.zhicore.content.interfaces.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章列表项响应 DTO
 * 
 * API 层统计字段统一使用 int
 * 
 * @author ZhiCore Team
 */
@Data
@Builder
@Schema(description = "文章列表项响应")
public class PostListItemResponse {
    
    @Schema(description = "文章 ID", example = "1234567890123456789")
    private Long id;
    
    @Schema(description = "标题", example = "Spring Boot 最佳实践")
    private String title;
    
    @Schema(description = "摘要", example = "本文介绍 Spring Boot 开发的最佳实践...")
    private String excerpt;
    
    @Schema(description = "封面图片 URL", example = "https://example.com/cover.jpg")
    private String coverImage;
    
    @Schema(description = "作者 ID", example = "1234567890")
    private Long authorId;
    
    @Schema(description = "作者名称", example = "张三")
    private String authorName;
    
    @Schema(description = "作者头像 URL", example = "https://example.com/avatar.jpg")
    private String authorAvatar;
    
    @Schema(description = "浏览数", example = "1000")
    private int viewCount;
    
    @Schema(description = "点赞数", example = "50")
    private int likeCount;
    
    @Schema(description = "评论数", example = "20")
    private int commentCount;
    
    @Schema(description = "分享数", example = "10")
    private int shareCount;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
