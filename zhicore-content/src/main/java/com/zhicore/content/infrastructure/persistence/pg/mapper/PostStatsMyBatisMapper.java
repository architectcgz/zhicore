package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostStatsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

/**
 * PostStats MyBatis Mapper
 * 
 * 提供 post_stats 表的数据访问操作。
 * 继承 MyBatis-Plus BaseMapper，自动提供基础 CRUD 方法。
 * 
 * @author ZhiCore Team
 */
@Mapper
public interface PostStatsMyBatisMapper extends BaseMapper<PostStatsEntity> {

    @Insert("""
            INSERT INTO post_stats (post_id, view_count, like_count, comment_count, favorite_count, share_count, last_updated_at)
            VALUES (#{postId}, 0, 1, 0, 0, 0, #{updatedAt})
            ON CONFLICT (post_id) DO UPDATE
            SET like_count = post_stats.like_count + 1,
                last_updated_at = #{updatedAt}
            """)
    int incrementLikeCount(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);

    @Insert("""
            INSERT INTO post_stats (post_id, view_count, like_count, comment_count, favorite_count, share_count, last_updated_at)
            VALUES (#{postId}, 0, 0, 0, 0, 0, #{updatedAt})
            ON CONFLICT (post_id) DO UPDATE
            SET like_count = GREATEST(0, post_stats.like_count - 1),
                last_updated_at = #{updatedAt}
            """)
    int decrementLikeCount(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);

    @Insert("""
            INSERT INTO post_stats (post_id, view_count, like_count, comment_count, favorite_count, share_count, last_updated_at)
            VALUES (#{postId}, 0, 0, 0, 1, 0, #{updatedAt})
            ON CONFLICT (post_id) DO UPDATE
            SET favorite_count = post_stats.favorite_count + 1,
                last_updated_at = #{updatedAt}
            """)
    int incrementFavoriteCount(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);

    @Insert("""
            INSERT INTO post_stats (post_id, view_count, like_count, comment_count, favorite_count, share_count, last_updated_at)
            VALUES (#{postId}, 0, 0, 0, 0, 0, #{updatedAt})
            ON CONFLICT (post_id) DO UPDATE
            SET favorite_count = GREATEST(0, post_stats.favorite_count - 1),
                last_updated_at = #{updatedAt}
            """)
    int decrementFavoriteCount(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);

    @Insert("""
            INSERT INTO post_stats (post_id, view_count, like_count, comment_count, favorite_count, share_count, last_updated_at)
            VALUES (#{postId}, 0, 0, 1, 0, 0, #{updatedAt})
            ON CONFLICT (post_id) DO UPDATE
            SET comment_count = post_stats.comment_count + 1,
                last_updated_at = #{updatedAt}
            """)
    int incrementCommentCount(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);

    @Insert("""
            INSERT INTO post_stats (post_id, view_count, like_count, comment_count, favorite_count, share_count, last_updated_at)
            VALUES (#{postId}, 0, 0, 0, 0, 0, #{updatedAt})
            ON CONFLICT (post_id) DO UPDATE
            SET comment_count = GREATEST(0, post_stats.comment_count - 1),
                last_updated_at = #{updatedAt}
            """)
    int decrementCommentCount(@Param("postId") Long postId, @Param("updatedAt") OffsetDateTime updatedAt);
}
