package com.zhicore.content.infrastructure.persistence.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章文档（MongoDB）
 * 存储文章的完整信息，包括标签信息，用于读模型
 *
 * @author ZhiCore Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "posts")
@CompoundIndexes({
    @CompoundIndex(name = "authorId_publishedAt_idx", def = "{'authorId': 1, 'publishedAt': -1}"),
    @CompoundIndex(name = "status_publishedAt_idx", def = "{'status': 1, 'publishedAt': -1}"),
    @CompoundIndex(name = "categoryId_publishedAt_idx", def = "{'categoryId': 1, 'publishedAt': -1}")
})
public class PostDocument {

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
     * 标题
     */
    private String title;

    /**
     * 内容（Markdown原文）
     */
    private String content;

    /**
     * 摘要
     */
    private String excerpt;

    /**
     * 作者ID
     */
    @Indexed
    private String authorId;

    /**
     * 作者名称
     */
    private String authorName;

    /**
     * 标签列表
     */
    private List<TagInfo> tags;

    /**
     * 分类ID
     */
    @Indexed
    private String categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 文章状态
     */
    @Indexed
    private String status;

    /**
     * 浏览数
     */
    private Integer viewCount;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 发布时间
     */
    @Indexed
    private LocalDateTime publishedAt;

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
     * 标签信息（嵌套文档）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagInfo {
        /**
         * 标签ID
         */
        private String id;

        /**
         * 标签名称
         */
        private String name;

        /**
         * 标签slug（URL友好标识）
         */
        private String slug;
    }
}
