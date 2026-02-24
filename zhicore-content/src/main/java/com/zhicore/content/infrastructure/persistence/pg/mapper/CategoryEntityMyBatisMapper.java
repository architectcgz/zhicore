package com.zhicore.content.infrastructure.persistence.pg.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.persistence.pg.entity.CategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * CategoryEntity MyBatis Mapper
 * 
 * 使用 MyBatis-Plus 提供基础 CRUD 操作，自定义查询使用注解或 XML。
 * 
 * @author ZhiCore Team
 */
@Mapper
public interface CategoryEntityMyBatisMapper extends BaseMapper<CategoryEntity> {

    /**
     * 查询所有顶级分类（没有父分类）
     * 
     * @return 顶级分类列表
     */
    @Select("""
            SELECT * FROM categories 
            WHERE parent_id IS NULL
            ORDER BY sort_order ASC, name ASC
            """)
    List<CategoryEntity> selectTopLevel();

    /**
     * 根据父分类ID查询子分类
     * 
     * @param parentId 父分类ID
     * @return 子分类列表
     */
    @Select("""
            SELECT * FROM categories 
            WHERE parent_id = #{parentId}
            ORDER BY sort_order ASC, name ASC
            """)
    List<CategoryEntity> selectByParentId(Long parentId);
}
