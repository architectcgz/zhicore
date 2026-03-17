package com.zhicore.content.domain.repository;

import com.zhicore.content.domain.model.PostLike;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文章点赞仓储接口
 *
 * @author ZhiCore Team
 */
public interface PostLikeRepository {

    /**
     * 保存点赞
     */
    boolean save(PostLike like);

    /**
     * 删除点赞
     */
    boolean delete(Long postId, Long userId);

    /**
     * 查询点赞记录
     */
    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    /**
     * 检查是否已点赞
     */
    boolean exists(Long postId, Long userId);

    /**
     * 查询用户点赞的文章列表（游标分页）
     */
    List<PostLike> findByUserIdCursor(Long userId, LocalDateTime cursor, int limit);

    /**
     * 统计文章点赞数
     */
    int countByPostId(Long postId);

    /**
     * 批量检查点赞状态
     */
    List<Long> findLikedPostIds(Long userId, List<Long> postIds);
}
