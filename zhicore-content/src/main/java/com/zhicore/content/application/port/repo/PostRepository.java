package com.zhicore.content.application.port.repo;

import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Post 聚合根仓储端口接口
 * 
 * 定义 Post 聚合的持久化操作契约，由基础设施层实现。
 * 遵循端口-适配器模式，应用层通过此接口访问持久化能力。
 * 
 * @author ZhiCore Team
 */
public interface PostRepository {
    
    /**
     * 加载文章（不存在时抛出异常）
     * 
     * @param id 文章ID（值对象）
     * @return 文章聚合根
     * @throws com.zhicore.common.exception.DomainException 文章不存在时
     */
    Post load(PostId id);
    
    /**
     * 查找文章（不存在时返回空）
     * 
     * @param id 文章ID（值对象）
     * @return 文章聚合根（可能为空）
     */
    Optional<Post> findById(PostId id);
    
    /**
     * 保存新文章
     * 
     * @param post 文章聚合根
     * @return 文章ID（值对象）
     */
    PostId save(Post post);
    
    /**
     * 更新文章
     * 
     * @param post 文章聚合根
     */
    void update(Post post);
    
    /**
     * 删除文章
     * 
     * @param id 文章ID（值对象）
     */
    void delete(PostId id);
    
    /**
     * 根据作者查询文章列表
     * 
     * @param authorId 作者ID（值对象）
     * @param pageable 分页参数
     * @return 文章列表
     */
    List<Post> findByAuthor(UserId authorId, Pageable pageable);
    
    /**
     * 根据标签查询文章列表
     * 
     * @param tagId 标签ID（值对象）
     * @param pageable 分页参数
     * @return 文章列表
     */
    List<Post> findByTag(TagId tagId, Pageable pageable);
    
    /**
     * 查询最新文章列表
     * 
     * @param pageable 分页参数
     * @return 文章列表
     */
    List<Post> findLatest(Pageable pageable);
    
    /**
     * 根据写入状态查询文章列表
     * 
     * 用于清理任务查询标记为 INCOMPLETE 的文章
     * 
     * @param writeState 写入状态
     * @return 文章列表
     */
    List<Post> findByWriteState(String writeState);

    // ==================== 扩展查询与管理接口 ====================

    Optional<Post> findById(Long id);

    Map<Long, Post> findByIds(List<Long> ids);

    List<Post> findByOwnerId(Long ownerId, PostStatus status, int offset, int limit);

    List<Post> findPublished(int offset, int limit);

    default List<Post> findPublishedCursor(LocalDateTime cursor, int limit) {
        return findPublishedCursor(cursor, null, limit);
    }

    /**
     * 复合游标分页（published_at + id）
     *
     * 约束：ORDER BY published_at DESC, id DESC
     */
    List<Post> findPublishedCursor(LocalDateTime cursorPublishedAt, Long cursorPostId, int limit);

    /**
     * 热门排序（偏移分页）
     */
    List<Post> findPublishedPopular(int offset, int limit);

    /**
     * 定时发布幂等条件更新（R1）
     *
     * @return 若成功发布，返回新的 version；否则返回 empty（幂等 no-op）
     */
    Optional<Long> publishScheduledIfNeeded(Long postId, LocalDateTime publishedAt);

    long countPublished();

    List<Post> findByConditions(String keyword, String status, Long authorId, int offset, int limit);

    long countByConditions(String keyword, String status, Long authorId);

    int updateAuthorInfo(Long userId, String nickname, String avatarId, Long version);

    List<Post> findByOwnerNameAndVersion(String ownerName, Long version, int limit);
}

