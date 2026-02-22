package com.zhicore.content.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.repository.po.PostStatsPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 文章统计 Mapper
 *
 * @author ZhiCore Team
 */
@Mapper
public interface PostStatsMapper extends BaseMapper<PostStatsPO> {

    /**
     * 增加点赞数
     */
    @Update("UPDATE post_stats SET like_count = like_count + 1 WHERE post_id = #{postId}")
    int incrementLikeCount(@Param("postId") String postId);

    /**
     * 减少点赞数
     */
    @Update("UPDATE post_stats SET like_count = GREATEST(0, like_count - 1) WHERE post_id = #{postId}")
    int decrementLikeCount(@Param("postId") String postId);

    /**
     * 增加收藏数
     */
    @Update("UPDATE post_stats SET favorite_count = favorite_count + 1 WHERE post_id = #{postId}")
    int incrementFavoriteCount(@Param("postId") String postId);

    /**
     * 减少收藏数
     */
    @Update("UPDATE post_stats SET favorite_count = GREATEST(0, favorite_count - 1) WHERE post_id = #{postId}")
    int decrementFavoriteCount(@Param("postId") String postId);

    /**
     * 增加评论数
     */
    @Update("UPDATE post_stats SET comment_count = comment_count + 1 WHERE post_id = #{postId}")
    int incrementCommentCount(@Param("postId") String postId);

    /**
     * 减少评论数
     */
    @Update("UPDATE post_stats SET comment_count = GREATEST(0, comment_count - 1) WHERE post_id = #{postId}")
    int decrementCommentCount(@Param("postId") String postId);

    /**
     * 增加浏览数
     */
    @Update("UPDATE post_stats SET view_count = view_count + 1 WHERE post_id = #{postId}")
    int incrementViewCount(@Param("postId") String postId);
}
