package com.zhicore.comment.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.comment.infrastructure.repository.po.CommentLikePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评论点赞 Mapper
 *
 * @author ZhiCore Team
 */
@Mapper
public interface CommentLikeMapper extends BaseMapper<CommentLikePO> {

    /**
     * 幂等插入点赞记录，利用唯一约束做幂等
     * 返回受影响行数：1 表示实际插入，0 表示已存在
     */
    @Insert("""
            INSERT INTO comment_likes (comment_id, user_id, created_at)
            VALUES (#{commentId}, #{userId}, NOW())
            ON CONFLICT (comment_id, user_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /**
     * 检查是否已点赞
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM comment_likes
            WHERE comment_id = #{commentId} AND user_id = #{userId}
            """)
    boolean exists(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /**
     * 删除点赞
     */
    @Delete("""
            DELETE FROM comment_likes
            WHERE comment_id = #{commentId} AND user_id = #{userId}
            """)
    int deleteByCommentIdAndUserId(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /**
     * 统计评论点赞数
     */
    @Select("""
            SELECT COUNT(*) FROM comment_likes
            WHERE comment_id = #{commentId}
            """)
    int countByCommentId(@Param("commentId") Long commentId);

    /**
     * 查询用户已点赞的评论ID列表
     */
    @Select("""
            <script>
            SELECT comment_id FROM comment_likes
            WHERE user_id = #{userId} AND comment_id IN
            <foreach collection="commentIds" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<Long> findLikedCommentIds(@Param("userId") Long userId, @Param("commentIds") List<Long> commentIds);
}
