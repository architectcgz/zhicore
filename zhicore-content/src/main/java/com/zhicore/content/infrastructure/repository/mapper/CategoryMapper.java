package com.zhicore.content.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.repository.po.CategoryPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类 Mapper
 *
 * @author ZhiCore Team
 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryPO> {
}
