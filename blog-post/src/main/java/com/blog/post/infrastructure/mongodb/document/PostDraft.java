package com.blog.post.infrastructure.mongodb.document;

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

/**
 * 文章草稿文档（MongoDB）
 * 支持自动保存功能，每个用户每篇文章只保留一份草稿（Upsert模式）
 *
 * @author Blog Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "post_drafts")
@CompoundIndexes({
    @CompoundIndex(name = "postId_userId_idx", def = "{'postId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(name = "userId_savedAt_idx", def = "{'userId': 1, 'savedAt': -1}")
})
public class PostDraft {

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
     * 用户ID
     */
    private String userId;

    /**
     * 草稿内容
     */
    private String content;

    /**
     * 内容类型：markdown/html/rich
     */
    private String contentType;

    /**
     * 保存时间
     */
    @Indexed(expireAfterSeconds = 2592000) // 30天自动过期
    private LocalDateTime savedAt;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 是否自动保存
     */
    private Boolean isAutoSave;

    /**
     * 字数统计
     */
    private Integer wordCount;
}
