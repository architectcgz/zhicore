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
