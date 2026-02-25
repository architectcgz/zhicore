package com.zhicore.content.infrastructure.persistence.pg.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Tag Statistics MyBatis Mapper
 * 
 * 管理 tag_stats 表的数据操作
 *
 * @author ZhiCore Team
 */
@Mapper
public interface TagStatsEntityMyBatisMapper {

    /**
     * 更新标签统计（基于 post_tags 表实时计算）
     * 
     * 使用 INSERT ... ON CONFLICT DO UPDATE 实现 upsert 操作
     * 
     * @param tagId 标签ID
     */
    void upsertTagStats(@Param("tagId") Long tagId);

    /**
     * 批量更新标签统计
     * 
     * @param tagIds 标签ID列表
     */
    void batchUpsertTagStats(@Param("tagIds") java.util.List<Long> tagIds);

    /**
     * 删除标签统计
     * 
     * @param tagId 标签ID
     */
    void deleteTagStats(@Param("tagId") Long tagId);
}
