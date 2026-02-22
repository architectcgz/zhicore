package com.zhicore.content.domain.repository;

import com.zhicore.content.domain.model.Category;

import java.util.List;
import java.util.Optional;

/**
 * 分类仓储接口
 *
 * @author ZhiCore Team
 */
public interface CategoryRepository {

    /**
     * 保存分类
     */
    void save(Category category);

    /**
     * 更新分类
     */
    void update(Category category);

    /**
     * 根据ID查询分类
     */
    Optional<Category> findById(String id);

    /**
     * 根据slug查询分类
     */
    Optional<Category> findBySlug(String slug);

    /**
     * 查询所有分类
     */
    List<Category> findAll();

    /**
     * 查询顶级分类
     */
    List<Category> findTopLevel();

    /**
     * 查询子分类
     */
    List<Category> findByParentId(String parentId);

    /**
     * 删除分类
     */
    void delete(String id);

    /**
     * 检查slug是否存在
     */
    boolean existsBySlug(String slug);
}
