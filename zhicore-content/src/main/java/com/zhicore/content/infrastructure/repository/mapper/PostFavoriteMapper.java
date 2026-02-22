package com.zhicore.content.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.repository.po.PostFavoritePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章收藏 Mapper
 *
 * @author ZhiCore Team
 */
@Mapper
public interface PostFavoriteMapper extends BaseMapper<PostFavoritePO> {

    /**
     * 查询用户收藏的文章（游标分页）
     */
    @Select("""
            SELECT * FROM post_favorites 
            WHERE user_id = #{userId}
            AND (#{cursor} IS NULL OR created_at < #{cursor})
            ORDER BY created_at DESC 
            LIMIT #{limit}
            """)
    List<PostFavoritePO> findByUserIdCursor(@Param("userId") Long userId,
                                             @Param("cursor") LocalDateTime cursor,
                                             @Param("limit") int limit);

    /**
     * 批量查询用户收藏的文章ID
     */
    @Select("""
            <script>
            SELECT post_id FROM post_favorites 
            WHERE user_id = #{userId}
            AND post_id IN 
            <foreach collection="postIds" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<Long> findFavoritedPostIds(@Param("userId") Long userId,
                                     @Param("postIds") List<Long> postIds);
}
