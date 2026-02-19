package com.blog.post.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.post.infrastructure.repository.po.CategoryPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类 Mapper
 *
 * @author Blog Team
 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryPO> {
}
