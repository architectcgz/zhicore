package com.zhicore.content.infrastructure.mongodb.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 文章版本历史文档（MongoDB）
 * 记录文章的每次编辑历史，支持版本追踪和恢复
 *
 * @author ZhiCore Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "post_versions")
@CompoundIndexes({
    @CompoundIndex(name = "postId_version_idx", def = "{'postId': 1, 'version': -1}"),
    @CompoundIndex(name = "postId_editedAt_idx", def = "{'postId': 1, 'editedAt': -1}")
})
public class PostVersion {

    /**
     * MongoDB 文档ID
     */
    @Id
    private String id;

    /**
     * 文章ID
     */
    private String postId;

    /**
     * 版本号（递增）
     */
    private Integer version;

    /**
     * 内容快照
     */
    private String content;

    /**
     * 内容类型：markdown/html/rich
     */
    private String contentType;

    /**
     * 编辑者ID
     */
    private String editedBy;

    /**
     * 编辑时间
     */
    private LocalDateTime editedAt;

    /**
     * 变更说明
     */
    private String changeNote;

    /**
     * 其他元数据（JSON格式）
     */
    private Object metadata;
}
