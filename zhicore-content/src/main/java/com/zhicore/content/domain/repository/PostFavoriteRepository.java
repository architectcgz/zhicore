package com.zhicore.content.domain.repository;

import com.zhicore.content.domain.model.PostFavorite;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;

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
    boolean delete(PostId postId, UserId userId);

    /**
     * 查询收藏记录
     */
    Optional<PostFavorite> findByPostIdAndUserId(PostId postId, UserId userId);

    /**
     * 检查是否已收藏
     */
    boolean exists(PostId postId, UserId userId);

    /**
     * 查询用户收藏的文章列表（游标分页）
     */
    List<PostFavorite> findByUserIdCursor(UserId userId, LocalDateTime cursor, int limit);

    /**
     * 统计文章收藏数
     */
    int countByPostId(PostId postId);

    /**
     * 批量检查收藏状态
     */
    List<PostId> findFavoritedPostIds(UserId userId, List<PostId> postIds);
}
