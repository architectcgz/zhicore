package com.blog.post.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.post.infrastructure.repository.po.TagPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 标签 Mapper
 *
 * @author Blog Team
 */
@Mapper
public interface TagMapper extends BaseMapper<TagPO> {

    /**
     * 获取热门标签（按文章数量排序）
     * 
     * @param limit 限制数量
     * @return 标签统计列表（包含 tag_id 和 post_count）
     */
    List<Map<String, Object>> findHotTags(@Param("limit") int limit);

    /**
     * 批量查询文章的标签（避免 N+1 查询）
     * 
     * @param postIds 文章ID列表
     * @return 标签列表（包含 post_id 字段用于分组）
     */
    List<Map<String, Object>> selectTagsByPostIds(@Param("postIds") List<Long> postIds);
}
