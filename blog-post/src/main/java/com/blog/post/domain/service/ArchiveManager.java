package com.blog.post.domain.service;

import com.blog.post.infrastructure.mongodb.document.PostArchive;

import java.util.Optional;

/**
 * 归档管理器接口
 * 管理内容归档和冷热分离，将不活跃的文章内容迁移到MongoDB以减轻PostgreSQL压力
 *
 * @author Blog Team
 */
public interface ArchiveManager {

    /**
     * 归档文章内容
     * 将文章的完整快照存储到MongoDB，并从PostgreSQL中删除内容字段
     *
     * @param postId 文章ID
     * @param reason 归档原因（time/inactive/manual）
     */
    void archivePost(Long postId, String reason);

    /**
     * 恢复归档内容
     * 将已归档的文章恢复为热数据
     *
     * @param postId 文章ID
     */
    void restorePost(Long postId);

    /**
     * 查询归档内容
     * 获取已归档文章的完整内容
     *
     * @param postId 文章ID
     * @return 归档内容
     */
    Optional<PostArchive> getArchivedContent(Long postId);

    /**
     * 批量归档
     * 根据时间阈值批量归档不活跃的文章
     *
     * @param threshold 归档阈值（天数）
     * @return 归档数量
     */
    int batchArchive(int threshold);

    /**
     * 检查是否已归档
     * 判断指定文章是否已被归档
     *
     * @param postId 文章ID
     * @return 是否已归档
     */
    boolean isArchived(Long postId);
}
