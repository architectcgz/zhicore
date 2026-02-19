package com.blog.comment.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.comment.infrastructure.repository.po.CommentPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论 Mapper
 *
 * @author Blog Team
 */
@Mapper
public interface CommentMapper extends BaseMapper<CommentPO> {

    // ==================== 顶级评论 - 传统分页（Web端）====================

    @Select("""
            SELECT c.*, COALESCE(cs.like_count, 0) as like_count, COALESCE(cs.reply_count, 0) as reply_count
            FROM comments c
            LEFT JOIN comment_stats cs ON c.id = cs.comment_id
            WHERE c.post_id = #{postId} AND c.parent_id IS NULL AND c.status = 0
            ORDER BY c.created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<CommentPO> findTopLevelByPostIdOrderByTimeOffset(
            @Param("postId") Long postId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            SELECT c.*, COALESCE(cs.like_count, 0) as like_count, COALESCE(cs.reply_count, 0) as reply_count
            FROM comments c
            LEFT JOIN comment_stats cs ON c.id = cs.comment_id
            WHERE c.post_id = #{postId} AND c.parent_id IS NULL AND c.status = 0
            ORDER BY COALESCE(cs.like_count, 0) DESC, c.id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<CommentPO> findTopLevelByPostIdOrderByLikesOffset(
            @Param("postId") Long postId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            SELECT COUNT(*) FROM comments
            WHERE post_id = #{postId} AND parent_id IS NULL AND status = 0
            """)
    long countTopLevelByPostId(@Param("postId") Long postId);

    // ==================== 顶级评论 - 游标分页（移动端）====================

    @Select("""
            <script>
            SELECT c.*, COALESCE(cs.like_count, 0) as like_count, COALESCE(cs.reply_count, 0) as reply_count
            FROM comments c
            LEFT JOIN comment_stats cs ON c.id = cs.comment_id
            WHERE c.post_id = #{postId} AND c.parent_id IS NULL AND c.status = 0
            <if test="cursorTime != null">
                AND (c.created_at &lt; #{cursorTime}
                     OR (c.created_at = #{cursorTime} AND c.id &lt; #{cursorId}))
            </if>
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT #{size}
            </script>
            """)
    List<CommentPO> findTopLevelByPostIdOrderByTimeCursor(
            @Param("postId") Long postId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("size") int size
    );

    @Select("""
            <script>
            SELECT c.*, COALESCE(cs.like_count, 0) as like_count, COALESCE(cs.reply_count, 0) as reply_count
            FROM comments c
            LEFT JOIN comment_stats cs ON c.id = cs.comment_id
            WHERE c.post_id = #{postId} AND c.parent_id IS NULL AND c.status = 0
            <if test="cursorLikeCount != null">
                AND (
                    COALESCE(cs.like_count, 0) &lt; #{cursorLikeCount}
                    OR (COALESCE(cs.like_count, 0) = #{cursorLikeCount} AND c.id &lt; #{cursorId})
                )
            </if>
            ORDER BY COALESCE(cs.like_count, 0) DESC, c.id DESC
            LIMIT #{size}
            </script>
            """)
    List<CommentPO> findTopLevelByPostIdOrderByLikesCursor(
            @Param("postId") Long postId,
            @Param("cursorLikeCount") Integer cursorLikeCount,
            @Param("cursorId") Long cursorId,
            @Param("size") int size
    );

    // ==================== 回复列表 - 传统分页（Web端）====================

    @Select("""
            SELECT c.*, COALESCE(cs.like_count, 0) as like_count
            FROM comments c
            LEFT JOIN comment_stats cs ON c.id = cs.comment_id
            WHERE c.root_id = #{rootId} AND c.parent_id IS NOT NULL AND c.status = 0
            ORDER BY c.created_at ASC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<CommentPO> findRepliesByRootIdOffset(
            @Param("rootId") Long rootId,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("""
            SELECT COUNT(*) FROM comments
            WHERE root_id = #{rootId} AND parent_id IS NOT NULL AND status = 0
            """)
    long countRepliesByRootId(@Param("rootId") Long rootId);

    // ==================== 回复列表 - 游标分页（移动端）====================

    @Select("""
            <script>
            SELECT c.*, COALESCE(cs.like_count, 0) as like_count
            FROM comments c
            LEFT JOIN comment_stats cs ON c.id = cs.comment_id
            WHERE c.root_id = #{rootId} AND c.parent_id IS NOT NULL AND c.status = 0
            <if test="cursorTime != null">
                AND (c.created_at &gt; #{cursorTime}
                     OR (c.created_at = #{cursorTime} AND c.id &gt; #{cursorId}))
            </if>
            ORDER BY c.created_at ASC, c.id ASC
            LIMIT #{size}
            </script>
            """)
    List<CommentPO> findRepliesByRootIdCursor(
            @Param("rootId") Long rootId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("size") int size
    );

    // ==================== 热门回复（预加载）====================

    @Select("""
            SELECT c.*, COALESCE(cs.like_count, 0) as like_count
            FROM comments c
            LEFT JOIN comment_stats cs ON c.id = cs.comment_id
            WHERE c.root_id = #{rootId} AND c.parent_id IS NOT NULL AND c.status = 0
            ORDER BY COALESCE(cs.like_count, 0) DESC, c.created_at ASC
            LIMIT #{limit}
            """)
    List<CommentPO> findHotRepliesByRootId(
            @Param("rootId") Long rootId,
            @Param("limit") int limit
    );

    // ==================== 管理员查询 ====================

    @Select("""
            <script>
            SELECT c.*, COALESCE(cs.like_count, 0) as like_count, COALESCE(cs.reply_count, 0) as reply_count
            FROM comments c
            LEFT JOIN comment_stats cs ON c.id = cs.comment_id
            WHERE 1=1
            <if test="keyword != null and keyword != ''">
                AND c.content LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="postId != null">
                AND c.post_id = #{postId}
            </if>
            <if test="userId != null">
                AND c.author_id = #{userId}
            </if>
            ORDER BY c.created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CommentPO> selectByConditions(
            @Param("keyword") String keyword,
            @Param("postId") Long postId,
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM comments c
            WHERE 1=1
            <if test="keyword != null and keyword != ''">
                AND c.content LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="postId != null">
                AND c.post_id = #{postId}
            </if>
            <if test="userId != null">
                AND c.author_id = #{userId}
            </if>
            </script>
            """)
    long countByConditions(
            @Param("keyword") String keyword,
            @Param("postId") Long postId,
            @Param("userId") Long userId
    );
}
