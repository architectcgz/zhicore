package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostFavoriteEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 文章收藏 Mapper（R3）
 */
@Mapper
public interface PostFavoriteMapper extends BaseMapper<PostFavoriteEntity> {

    @Insert("""
            INSERT INTO post_favorites (id, post_id, user_id, created_at)
            VALUES (#{entity.id}, #{entity.postId}, #{entity.userId}, #{entity.createdAt})
            ON CONFLICT (post_id, user_id) DO NOTHING
            """)
    int insertIgnoreConflict(@Param("entity") PostFavoriteEntity entity);

    @Delete("""
            DELETE FROM post_favorites
            WHERE post_id = #{postId}
              AND user_id = #{userId}
            """)
    int deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);
}
