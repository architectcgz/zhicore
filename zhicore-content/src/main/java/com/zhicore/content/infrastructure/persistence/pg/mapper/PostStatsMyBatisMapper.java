package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostStatsEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * PostStats MyBatis Mapper
 * 
 * 提供 post_stats 表的数据访问操作。
 * 继承 MyBatis-Plus BaseMapper，自动提供基础 CRUD 方法。
 * 
 * @author ZhiCore Team
 */
@Mapper
public interface PostStatsMyBatisMapper extends BaseMapper<PostStatsEntity> {
    // MyBatis-Plus 自动提供以下方法：
    // - selectById(Long id)
    // - selectBatchIds(List<Long> ids)
    // - insert(PostStatsEntity entity)
    // - updateById(PostStatsEntity entity)
    // - deleteById(Long id)
}
