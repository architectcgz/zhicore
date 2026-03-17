package com.zhicore.content.domain.repository;

import com.zhicore.content.domain.model.PostFavorite;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文章收藏仓储接口
 *
 * @author ZhiCore Team
 */
public interface PostFavoriteRepository {

    /**
     * 保存收藏
     */
    boolean save(PostFavorite favorite);

    /**
     * 删除收藏
     */
    boolean delete(Long postId, Long userId);

    /**
     * 查询收藏记录
     */
    Optional<PostFavorite> findByPostIdAndUserId(Long postId, Long userId);

    /**
     * 检查是否已收藏
     */
    boolean exists(Long postId, Long userId);

    /**
     * 查询用户收藏的文章列表（游标分页）
     */
    List<PostFavorite> findByUserIdCursor(Long userId, LocalDateTime cursor, int limit);

    /**
     * 统计文章收藏数
     */
    int countByPostId(Long postId);

    /**
     * 批量检查收藏状态
     */
    List<Long> findFavoritedPostIds(Long userId, List<Long> postIds);
}
