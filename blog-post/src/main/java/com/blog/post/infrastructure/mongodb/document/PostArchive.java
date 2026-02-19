package com.blog.post.infrastructure.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 文章归档文档（MongoDB）
 * 存储冷数据的完整快照，用于冷热分离
 *
 * @author Blog Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "post_archives")
public class PostArchive {

    /**
     * MongoDB 文档ID
     */
    @Id
    private String id;

    /**
     * 文章ID
     */
    @Indexed(unique = true)
    private String postId;

    /**
     * 内容
     */
    private String content;

    /**
     * 内容类型：markdown/html/rich
     */
    private String contentType;

    /**
     * 归档时间
     */
    @Indexed
    private LocalDateTime archivedAt;

    /**
     * 归档原因：time/inactive/manual
     */
    private String archiveReason;

    /**
     * 完整快照（包含元数据，JSON格式）
     */
    private Object snapshot;
}
