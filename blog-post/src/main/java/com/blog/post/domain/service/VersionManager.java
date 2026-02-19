package com.blog.post.domain.service;

import com.blog.post.infrastructure.mongodb.document.PostVersion;
import org.springframework.data.domain.Page;

import java.util.Optional;

/**
 * 版本管理器接口
 * 管理文章的版本历史，支持版本追踪、查询和恢复
 *
 * @author Blog Team
 */
public interface VersionManager {

    /**
     * 创建新版本
     * 在用户更新文章内容时，创建版本快照以追踪变更历史
     *
     * @param postId 文章ID
     * @param content 内容快照
     * @param editedBy 编辑者ID
     * @param changeNote 变更说明
     * @return 版本号
     */
    Integer createVersion(String postId, String content, String editedBy, String changeNote);

    /**
     * 获取版本列表（分页）
     * 按编辑时间倒序返回版本列表
     *
     * @param postId 文章ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 版本列表
     */
    Page<PostVersion> getVersions(String postId, int page, int size);

    /**
     * 获取特定版本
     * 查询指定版本号的完整内容快照
     *
     * @param postId 文章ID
     * @param version 版本号
     * @return 版本详情
     */
    Optional<PostVersion> getVersion(String postId, Integer version);

    /**
     * 恢复到指定版本
     * 将指定版本的内容设置为当前内容，并创建新版本记录
     *
     * @param postId 文章ID
     * @param version 版本号
     * @return 新的版本号
     */
    Integer restoreVersion(String postId, Integer version);

    /**
     * 清理旧版本
     * 当版本数量超过限制时，自动删除最旧的版本记录
     *
     * @param postId 文章ID
     * @param keepCount 保留的版本数
     */
    void cleanOldVersions(String postId, int keepCount);
}
