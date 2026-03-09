package com.zhicore.comment.domain.repository;

import com.zhicore.comment.domain.model.CommentLike;

import java.util.List;

/**
 * 评论点赞仓储接口
 *
 * @author ZhiCore Team
 */
public interface CommentLikeRepository {

    /**
     * 保存点赞
     */
    void save(CommentLike like);

    /**
     * 幂等插入点赞记录，利用 DB 唯一约束做幂等
     *
     * @return true 表示实际插入成功，false 表示已存在（ON CONFLICT DO NOTHING）
     */
    boolean insertIfAbsent(Long commentId, Long userId);

    /**
     * 删除点赞记录
     *
     * @return true 表示实际删除了记录，false 表示记录不存在
     */
    boolean deleteAndReturnAffected(Long commentId, Long userId);

    /**
     * 删除点赞
     */
    void delete(Long commentId, Long userId);

    /**
     * 检查是否已点赞
     */
    boolean exists(Long commentId, Long userId);

    /**
     * 统计评论点赞数
     */
    int countByCommentId(Long commentId);

    /**
     * 查询用户已点赞的评论ID列表
     */
    List<Long> findLikedCommentIds(Long userId, List<Long> commentIds);
}
