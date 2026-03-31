package com.zhicore.comment.domain.repository;

import com.zhicore.comment.domain.model.Comment;
import com.zhicore.comment.domain.model.CommentStats;
import com.zhicore.comment.domain.cursor.HotCursorCodec.HotCursor;
import com.zhicore.comment.domain.cursor.TimeCursorCodec.TimeCursor;
import com.zhicore.common.result.PageResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 评论仓储接口
 *
 * @author ZhiCore Team
 */
public interface CommentRepository {

    /**
     * 根据ID查询评论
     */
    Optional<Comment> findById(Long id);

    /**
     * 批量根据ID查询评论
     * 
     * @param ids 评论ID集合
     * @return 评论列表
     */
    List<Comment> findByIds(Set<Long> ids);

    /**
     * 保存评论
     */
    void save(Comment comment);

    /**
     * 更新评论
     */
    void update(Comment comment);

    /**
     * 删除评论
     */
    void delete(Long id);

    // ========== 顶级评论查询 - 传统分页（Web端）==========

    /**
     * 按时间排序查询顶级评论（传统分页）
     */
    PageResult<Comment> findTopLevelByPostIdOrderByTimePage(Long postId, int page, int size);

    /**
     * 按热度排序查询顶级评论（传统分页）
     */
    PageResult<Comment> findTopLevelByPostIdOrderByLikesPage(Long postId, int page, int size);

    // ========== 顶级评论查询 - 游标分页（移动端）==========

    /**
     * 按时间排序查询顶级评论（游标分页）
     */
    List<Comment> findTopLevelByPostIdOrderByTimeCursor(Long postId, TimeCursor cursor, int size);

    /**
     * 按热度排序查询顶级评论（游标分页）
     */
    List<Comment> findTopLevelByPostIdOrderByLikesCursor(Long postId, HotCursor cursor, int size);

    // ========== 回复列表查询 ==========

    /**
     * 查询回复列表（传统分页）
     */
    PageResult<Comment> findRepliesByRootIdPage(Long rootId, int page, int size);

    /**
     * 查询回复列表（游标分页）
     */
    List<Comment> findRepliesByRootIdCursor(Long rootId, TimeCursor cursor, int size);

    /**
     * 按时间正序增量查询顶级评论。
     */
    List<Comment> findTopLevelByPostIdIncremental(Long postId, OffsetDateTime afterCreatedAt, Long afterId, int size);

    /**
     * 按时间正序增量查询回复。
     */
    List<Comment> findRepliesByRootIdIncremental(Long rootId, OffsetDateTime afterCreatedAt, Long afterId, int size);

    /**
     * 查询热门回复（预加载）
     */
    List<Comment> findHotRepliesByRootId(Long rootId, int limit);

    /**
     * 按根评论批量查询热门回复，避免列表页产生 N+1 查询。
     */
    List<Comment> findHotRepliesByRootIds(List<Long> rootIds, int limit);

    // ========== 统计查询 ==========

    /**
     * 统计顶级评论数
     */
    long countTopLevelByPostId(Long postId);

    /**
     * 统计回复数
     */
    int countRepliesByRootId(Long rootId);

    /**
     * 批量获取评论统计
     */
    Map<Long, CommentStats> batchGetStats(List<Long> commentIds);

    // ========== 管理员查询 ==========

    /**
     * 根据条件查询评论列表
     *
     * @param keyword 关键词（搜索内容）
     * @param postId 文章ID
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 评论列表
     */
    List<Comment> findByConditions(String keyword, Long postId, Long userId, int offset, int limit);

    /**
     * 根据条件统计评论数量
     *
     * @param keyword 关键词（搜索内容）
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 评论数量
     */
    long countByConditions(String keyword, Long postId, Long userId);
}
