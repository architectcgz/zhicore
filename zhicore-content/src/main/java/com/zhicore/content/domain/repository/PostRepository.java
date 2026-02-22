package com.zhicore.content.domain.repository;

import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文章仓储接口
 *
 * @author ZhiCore Team
 */
public interface PostRepository {

    /**
     * 保存文章
     */
    void save(Post post);

    /**
     * 更新文章
     */
    void update(Post post);

    /**
     * 根据ID查询文章
     */
    Optional<Post> findById(Long id);

    /**
     * 批量根据ID查询文章
     */
    Map<Long, Post> findByIds(List<Long> ids);

    /**
     * 根据作者ID查询文章列表（分页）
     */
    List<Post> findByOwnerId(Long ownerId, PostStatus status, int offset, int limit);

    /**
     * 根据作者ID查询文章列表（游标分页）
     */
    List<Post> findByOwnerIdCursor(Long ownerId, PostStatus status, LocalDateTime cursor, int limit);

    /**
     * 查询已发布文章列表（分页）
     */
    List<Post> findPublished(int offset, int limit);

    /**
     * 查询已发布文章列表（游标分页）
     */
    List<Post> findPublishedCursor(LocalDateTime cursor, int limit);

    /**
     * 查询定时发布到期的文章
     */
    List<Post> findScheduledPostsDue(LocalDateTime now);

    /**
     * 统计作者文章数
     */
    int countByOwnerId(Long ownerId, PostStatus status);

    /**
     * 统计已发布文章总数
     */
    long countPublished();

    /**
     * 删除文章
     */
    void delete(Long id);

    /**
     * 检查文章是否存在
     */
    boolean existsById(Long id);

    /**
     * 根据条件查询文章列表（用于管理后台）
     *
     * @param keyword 关键词（标题）
     * @param status 状态
     * @param authorId 作者ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 文章列表
     */
    List<Post> findByConditions(String keyword, String status, Long authorId, int offset, int limit);

    /**
     * 根据条件统计文章数量（用于管理后台）
     *
     * @param keyword 关键词（标题）
     * @param status 状态
     * @param authorId 作者ID
     * @return 文章数量
     */
    long countByConditions(String keyword, String status, Long authorId);

    /**
     * 批量更新作者信息（冗余字段）
     * 
     * 根据用户ID批量更新该用户所有文章的作者信息快照，包括昵称、头像和版本号。
     * 使用版本号过滤确保只更新旧版本的数据，防止消息乱序导致的数据不一致。
     *
     * @param userId 用户ID
     * @param nickname 新的昵称
     * @param avatarId 新的头像文件ID（可为null）
     * @param version 新的版本号
     * @return 更新的文章数量
     */
    int updateAuthorInfo(Long userId, String nickname, String avatarId, Long version);

    /**
     * 根据作者昵称和版本号查询文章列表
     * 
     * 用于补偿机制，查询指定作者昵称且版本号小于指定版本的文章。
     * 利用复合索引 idx_posts_owner_name_version 提高查询性能。
     *
     * @param ownerName 作者昵称
     * @param version 版本号（查询小于此版本的文章）
     * @param limit 限制返回数量
     * @return 文章列表
     */
    List<Post> findByOwnerNameAndVersion(String ownerName, Long version, int limit);
}
