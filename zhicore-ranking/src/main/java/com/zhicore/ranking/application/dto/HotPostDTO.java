package com.zhicore.ranking.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 热门文章 DTO
 * 包含文章的关键信息和热度数据
 *
 * @author ZhiCore Team
 */
@Schema(description = "热门文章信息")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotPostDTO {

    /**
     * 文章 ID
     */
    @Schema(description = "文章 ID", example = "1234567890")
    private String id;

    /**
     * 文章标题
     */
    @Schema(description = "文章标题", example = "Spring Boot 最佳实践")
    private String title;

    /**
     * 文章摘要
     */
    @Schema(description = "文章摘要", example = "本文介绍 Spring Boot 开发的最佳实践...")
    private String excerpt;

    /**
     * 封面图片 URL
     */
    @Schema(description = "封面图片 URL", example = "https://example.com/cover.jpg")
    private String coverImageUrl;

    /**
     * 作者 ID
     */
    @Schema(description = "作者 ID", example = "1001")
    private String ownerId;

    /**
     * 作者名称
     */
    @Schema(description = "作者名称", example = "张三")
    private String ownerName;

    /**
     * 作者头像
     */
    @Schema(description = "作者头像 URL", example = "https://example.com/avatar.jpg")
    private String ownerAvatar;

    /**
     * 话题 ID
     */
    @Schema(description = "话题 ID", example = "10")
    private Long topicId;

    /**
     * 话题名称
     */
    @Schema(description = "话题名称", example = "后端开发")
    private String topicName;

    /**
     * 发布时间
     */
    @Schema(description = "发布时间", example = "2024-01-28T10:30:00")
    private OffsetDateTime publishedAt;

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
     * 收藏数
     */
    @Schema(description = "收藏数", example = "89")
    private Integer favoriteCount;

    /**
     * 浏览数
     */
    @Schema(description = "浏览数", example = "1520")
    private Long viewCount;

    /**
     * 热度分数
     */
    @Schema(description = "热度分数", example = "1250.5")
    private Double hotScore;

    /**
     * 排名
     */
    @Schema(description = "排名（从1开始）", example = "1")
    private Integer rank;
}
