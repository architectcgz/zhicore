package com.zhicore.content.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.repository.po.PostPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章 Mapper
 *
 * @author ZhiCore Team
 */
@Mapper
public interface PostMapper extends BaseMapper<PostPO> {

    /**
     * 根据作者ID查询文章（游标分页）
     */
    @Select("""
            SELECT * FROM posts 
            WHERE owner_id = #{ownerId} 
            AND status = #{status}
            AND (#{cursor} IS NULL OR created_at < #{cursor})
            ORDER BY created_at DESC 
            LIMIT #{limit}
            """)
    List<PostPO> findByOwnerIdCursor(@Param("ownerId") Long ownerId,
                                      @Param("status") Integer status,
                                      @Param("cursor") LocalDateTime cursor,
                                      @Param("limit") int limit);

    /**
     * 查询已发布文章（游标分页）
     */
    @Select("""
            SELECT * FROM posts 
            WHERE status = 1
            AND (#{cursor} IS NULL OR published_at < #{cursor})
            ORDER BY published_at DESC 
            LIMIT #{limit}
            """)
    List<PostPO> findPublishedCursor(@Param("cursor") LocalDateTime cursor,
                                      @Param("limit") int limit);

    /**
     * 查询定时发布到期的文章
     */
    @Select("""
            SELECT * FROM posts 
            WHERE status = 2 
            AND scheduled_at <= #{now}
            """)
    List<PostPO> findScheduledPostsDue(@Param("now") LocalDateTime now);

    /**
     * 根据条件查询文章列表（用于管理后台）
     *
     * @param keyword 关键词（标题）
     * @param status 状态
     * @param authorId 作者ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 文章列表
     */
    List<PostPO> selectByConditions(@Param("keyword") String keyword,
                                     @Param("status") String status,
                                     @Param("authorId") Long authorId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 根据条件统计文章数量（用于管理后台）
     *
     * @param keyword 关键词（标题）
     * @param status 状态
     * @param authorId 作者ID
     * @return 文章数量
     */
    long countByConditions(@Param("keyword") String keyword,
                           @Param("status") String status,
                           @Param("authorId") Long authorId);

    /**
     * 批量更新作者信息（冗余字段）
     * 
     * 使用版本号过滤确保只更新旧版本的数据，防止消息乱序导致的数据不一致。
     * 
     * @param userId 用户ID
     * @param nickname 新的昵称
     * @param avatarId 新的头像文件ID（可为null）
     * @param version 新的版本号
     * @return 更新的文章数量
     */
    @Select("""
            UPDATE posts 
            SET owner_name = #{nickname},
                owner_avatar_id = #{avatarId},
                owner_profile_version = #{version},
                updated_at = CURRENT_TIMESTAMP
            WHERE owner_id = #{userId} 
              AND owner_profile_version < #{version}
            """)
    int updateAuthorInfo(@Param("userId") Long userId,
                         @Param("nickname") String nickname,
                         @Param("avatarId") String avatarId,
                         @Param("version") Long version);

    /**
     * 根据作者昵称和版本号查询文章列表
     * 
     * 用于补偿机制，查询指定作者昵称且版本号小于指定版本的文章。
     * 利用复合索引 idx_posts_owner_name_version 提高查询性能。
     * 
     * @param ownerName 作者昵称
     * @param version 版本号（查询小于此版本的文章）
     * @param limit 限制返回数量
     * @return 文章PO列表
     */
    @Select("""
            SELECT * FROM posts 
            WHERE owner_name = #{ownerName} 
              AND owner_profile_version < #{version}
            LIMIT #{limit}
            """)
    List<PostPO> findByOwnerNameAndVersion(@Param("ownerName") String ownerName,
                                            @Param("version") Long version,
                                            @Param("limit") int limit);
}
