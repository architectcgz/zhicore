package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * PostEntity MyBatis Mapper
 * 
 * 使用 MyBatis-Plus 提供基础 CRUD 操作，自定义查询使用注解或 XML。
 * 
 * @author ZhiCore Team
 */
@Mapper
public interface PostEntityMyBatisMapper extends BaseMapper<PostEntity> {

    /**
     * 根据作者ID查询文章列表（分页）
     * 
     * @param authorId 作者ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 文章实体列表
     */
    @Select("""
            SELECT * FROM posts 
            WHERE owner_id = #{authorId} 
            AND status != 3
            ORDER BY created_at DESC 
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<PostEntity> selectByAuthor(@Param("authorId") Long authorId,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /**
     * 根据标签ID查询文章列表（分页）
     * 
     * 通过 post_tags 关联表查询。
     * 
     * @param tagId 标签ID
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 文章实体列表
     */
    @Select("""
            SELECT p.* FROM posts p
            INNER JOIN post_tags pt ON p.id = pt.post_id
            WHERE pt.tag_id = #{tagId}
            AND p.status = 1
            ORDER BY p.published_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<PostEntity> selectByTag(@Param("tagId") Long tagId,
                                  @Param("offset") long offset,
                                  @Param("limit") int limit);

    /**
     * 查询最新文章列表（分页）
     * 
     * 只返回已发布的文章，按发布时间倒序。
     * 
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 文章实体列表
     */
    @Select("""
            SELECT * FROM posts 
            WHERE status = 1
            ORDER BY published_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<PostEntity> selectLatest(@Param("offset") long offset,
                                   @Param("limit") int limit);

    /**
     * 根据写入状态查询文章列表
     * 
     * 用于清理任务，查询标记为 INCOMPLETE 的文章。
     * 
     * @param writeState 写入状态
     * @return 文章实体列表
     */
    @Select("""
            SELECT * FROM posts 
            WHERE write_state = #{writeState}
            ORDER BY created_at ASC
            LIMIT 100
            """)
    List<PostEntity> selectByWriteState(@Param("writeState") String writeState);

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
}
