package com.zhicore.search.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章搜索结果 VO
 *
 * @author ZhiCore Team
 */
@Schema(description = "文章搜索结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostSearchVO {

    /**
     * 文章ID
     */
    @Schema(description = "文章ID", example = "1234567890")
    private String id;

    /**
     * 标题
     */
    @Schema(description = "文章标题", example = "Spring Boot 微服务实战")
    private String title;

    /**
     * 高亮标题（包含 <em> 标签）
     */
    @Schema(description = "高亮标题（包含 <em> 标签）", example = "<em>Spring Boot</em> 微服务实战")
    private String highlightTitle;

    /**
     * 摘要
     */
    @Schema(description = "文章摘要", example = "本文介绍如何使用 Spring Boot 构建微服务...")
    private String excerpt;

    /**
     * 高亮内容片段（包含 <em> 标签）
     */
    @Schema(description = "高亮内容片段（包含 <em> 标签）", example = "...使用 <em>Spring Boot</em> 可以快速构建...")
    private String highlightContent;

    /**
     * 作者ID
     */
    @Schema(description = "作者ID", example = "1001")
    private String authorId;

    /**
     * 作者名称
     */
    @Schema(description = "作者名称", example = "张三")
    private String authorName;

    /**
     * 标签列表
     */
    @Schema(description = "标签列表", example = "[\"Spring Boot\", \"微服务\", \"Java\"]")
    private List<String> tags;

    /**
     * 分类名称
     */
    @Schema(description = "分类名称", example = "后端开发")
    private String categoryName;

    /**
     * 点赞数
     */
    @Schema(description = "点赞数", example = "128")
    private Integer likeCount;

    /**
     * 评论数
     */
    @Schema(description = "评论数", example = "45")
    private Integer commentCount;

    /**
     * 浏览数
     */
    @Schema(description = "浏览数", example = "1520")
    private Long viewCount;

    /**
     * 发布时间
     */
    @Schema(description = "发布时间", example = "2024-01-15T10:30:00")
    private LocalDateTime publishedAt;

    /**
     * 搜索相关性分数
     */
    @Schema(description = "搜索相关性分数（越高越相关）", example = "8.5")
    private Float score;
}
