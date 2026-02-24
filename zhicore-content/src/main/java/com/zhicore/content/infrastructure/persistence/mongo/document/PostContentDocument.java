package com.zhicore.content.infrastructure.persistence.mongo.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 文章内容 MongoDB 文档
 * 
 * 存储文章的完整内容，与 PostgreSQL 中的元数据分离。
 * 支持大文本存储和独立扩展。
 * 
 * @author ZhiCore Team
 */
@Data
@Document(collection = "post_contents")
public class PostContentDocument {

    /**
     * MongoDB 文档ID
     */
    @Id
    private String id;

    /**
     * 文章ID（与 PostgreSQL 关联，唯一索引）
     */
    @Indexed(unique = true)
    private Long postId;

    /**
     * 文章内容（Markdown 或 HTML）
     */
    private String content;

    /**
     * 内容类型（markdown, html）
     */
    private String contentType;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Indexed
    private LocalDateTime updatedAt;
}
