package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.TagEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * TagEntity MyBatis Mapper
 * 
 * 使用 MyBatis-Plus 提供基础 CRUD 操作，自定义查询使用注解或 XML。
 * 
 * @author ZhiCore Team
 */
@Mapper
public interface TagEntityMyBatisMapper extends BaseMapper<TagEntity> {

    /**
     * 批量根据 slug 查询标签
     * 
     * @param slugs slug 列表
     * @return 标签实体列表
     */
    @Select("""
            <script>
            SELECT * FROM tags 
            WHERE slug IN
            <foreach collection='slugs' item='slug' open='(' separator=',' close=')'>
                #{slug}
            </foreach>
            </script>
            """)
    List<TagEntity> selectBySlugIn(@Param("slugs") List<String> slugs);

    /**
     * 根据名称模糊搜索标签
     * 
     * @param keyword 关键词
     * @param limit 限制数量
     * @return 标签实体列表
     */
    @Select("""
            SELECT * FROM tags 
            WHERE name ILIKE CONCAT('%', #{keyword}, '%')
            ORDER BY name ASC
            LIMIT #{limit}
            """)
    List<TagEntity> searchByName(@Param("keyword") String keyword, 
                                  @Param("limit") int limit);

    /**
     * 获取热门标签（按文章数量排序）
     * 
     * 从 tag_stats 表查询，按 post_count 降序排序。
     * 返回 Map 包含 tag_id 和 post_count。
     * 
     * @param limit 限制数量
     * @return 标签统计列表
     */
    @Select("""
            SELECT ts.tag_id, ts.post_count
            FROM tag_stats ts
            WHERE ts.post_count > 0
            ORDER BY ts.post_count DESC, ts.tag_id ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectHotTags(@Param("limit") int limit);

    /**
     * 批量查询文章的标签（避免 N+1 查询）
     * 
     * 用于文章列表场景，一次性加载多篇文章的标签信息。
     * 返回 Map 包含 post_id 和对应的标签实体。
     * 
     * @param postIds 文章ID列表
     * @return 文章ID到标签列表的映射
     */
    @Select("""
            <script>
            SELECT pt.post_id, t.*
            FROM post_tags pt
            INNER JOIN tags t ON pt.tag_id = t.id
            WHERE pt.post_id IN
            <foreach collection='postIds' item='postId' open='(' separator=',' close=')'>
                #{postId}
            </foreach>
            ORDER BY pt.post_id, t.name
            </script>
            """)
    List<Map<String, Object>> selectTagsByPostIds(@Param("postIds") List<Long> postIds);
}
