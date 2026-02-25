package com.zhicore.comment.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.comment.infrastructure.repository.po.CommentStatsPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 评论统计 Mapper
 *
 * @author ZhiCore Team
 */
@Mapper
public interface CommentStatsMapper extends BaseMapper<CommentStatsPO> {

    /**
     * 批量查询评论统计
     */
    @Select("""
            <script>
            SELECT * FROM comment_stats
            WHERE comment_id IN
            <foreach collection="commentIds" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<CommentStatsPO> batchSelectByCommentIds(@Param("commentIds") List<Long> commentIds);

    /**
     * 增加点赞数
     */
    @Update("""
            INSERT INTO comment_stats (comment_id, like_count, reply_count)
            VALUES (#{commentId}, 1, 0)
            ON CONFLICT (comment_id)
            DO UPDATE SET like_count = comment_stats.like_count + 1
            """)
    void incrementLikeCount(@Param("commentId") Long commentId);

    /**
     * 减少点赞数
     */
    @Update("""
            UPDATE comment_stats
            SET like_count = GREATEST(0, like_count - 1)
            WHERE comment_id = #{commentId}
            """)
    void decrementLikeCount(@Param("commentId") Long commentId);

    /**
     * 增加回复数
     */
    @Update("""
            INSERT INTO comment_stats (comment_id, like_count, reply_count)
            VALUES (#{commentId}, 0, 1)
            ON CONFLICT (comment_id)
            DO UPDATE SET reply_count = comment_stats.reply_count + 1
            """)
    void incrementReplyCount(@Param("commentId") Long commentId);

    /**
     * 减少回复数
     */
    @Update("""
            UPDATE comment_stats
            SET reply_count = GREATEST(0, reply_count - 1)
            WHERE comment_id = #{commentId}
            """)
    void decrementReplyCount(@Param("commentId") Long commentId);
}
