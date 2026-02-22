package com.zhicore.content.infrastructure.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章内容文档（MongoDB）
 * 存储文章的内容数据，包括原始内容、HTML渲染结果、纯文本等
 *
 * @author ZhiCore Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "post_contents")
public class PostContent {

    /**
     * MongoDB 文档ID
     */
    @Id
    private String id;

    /**
     * 文章ID（与PostgreSQL关联）
     */
    @Indexed(unique = true)
    private String postId;

    /**
     * 内容类型：markdown/html/rich
     */
    private String contentType;

    /**
     * Markdown原文
     */
    private String raw;

    /**
     * HTML渲染结果
     */
    private String html;

    /**
     * 纯文本（用于搜索）
     */
    private String text;

    /**
     * 字数统计
     */
    private Integer wordCount;

    /**
     * 预计阅读时间（分钟）
     */
    private Integer readingTime;

    /**
     * 富文本内容块（可选）
     */
    private List<ContentBlock> blocks;

    /**
     * 媒体资源（可选）
     */
    private List<MediaResource> media;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Indexed
    private LocalDateTime updatedAt;

    /**
     * 内容块
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentBlock {
        /**
         * 块类型：text/image/video/code/chart
         */
        private String type;

        /**
         * 块内容
         */
        private String content;

        /**
         * 块属性（JSON格式）
         */
        private Object props;

        /**
         * 排序顺序
         */
        private Integer order;
    }

    /**
     * 媒体资源
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaResource {
        /**
         * 资源类型：image/video/audio
         */
        private String type;

        /**
         * 资源URL
         */
        private String url;

        /**
         * 缩略图URL
         */
        private String thumbnail;

        /**
         * 文件大小（字节）
         */
        private Long size;

        /**
         * 元数据（JSON格式）
         */
        private Object metadata;
    }
}
