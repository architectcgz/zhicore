package com.zhicore.content.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhicore.content.domain.model.Category;
import com.zhicore.content.domain.repository.CategoryRepository;
import com.zhicore.content.infrastructure.repository.mapper.CategoryMapper;
import com.zhicore.content.infrastructure.repository.po.CategoryPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 分类仓储实现
 *
 * @author ZhiCore Team
 */
@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryMapper categoryMapper;

    @Override
    public void save(Category category) {
        CategoryPO po = toPO(category);
        categoryMapper.insert(po);
    }

    @Override
    public void update(Category category) {
        CategoryPO po = toPO(category);
        categoryMapper.updateById(po);
    }

    @Override
    public Optional<Category> findById(String id) {
        CategoryPO po = categoryMapper.selectById(id);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        LambdaQueryWrapper<CategoryPO> wrapper = new LambdaQueryWrapper<CategoryPO>()
                .eq(CategoryPO::getSlug, slug);
        CategoryPO po = categoryMapper.selectOne(wrapper);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<Category> findAll() {
        LambdaQueryWrapper<CategoryPO> wrapper = new LambdaQueryWrapper<CategoryPO>()
                .orderByAsc(CategoryPO::getSortOrder);
        return categoryMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Category> findTopLevel() {
        LambdaQueryWrapper<CategoryPO> wrapper = new LambdaQueryWrapper<CategoryPO>()
                .isNull(CategoryPO::getParentId)
                .orderByAsc(CategoryPO::getSortOrder);
        return categoryMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Category> findByParentId(String parentId) {
        LambdaQueryWrapper<CategoryPO> wrapper = new LambdaQueryWrapper<CategoryPO>()
                .eq(CategoryPO::getParentId, parentId)
                .orderByAsc(CategoryPO::getSortOrder);
        return categoryMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        categoryMapper.deleteById(id);
    }

    @Override
    public boolean existsBySlug(String slug) {
        LambdaQueryWrapper<CategoryPO> wrapper = new LambdaQueryWrapper<CategoryPO>()
                .eq(CategoryPO::getSlug, slug);
        return categoryMapper.selectCount(wrapper) > 0;
    }

    // ==================== 转换方法 ====================

    private CategoryPO toPO(Category category) {
        CategoryPO po = new CategoryPO();
        po.setId(category.getId());
        po.setName(category.getName());
        po.setSlug(category.getSlug());
        po.setDescription(category.getDescription());
        po.setParentId(category.getParentId());
        po.setSortOrder(category.getSortOrder());
        po.setCreatedAt(category.getCreatedAt());
        po.setUpdatedAt(category.getUpdatedAt());
        return po;
    }

    private Category toDomain(CategoryPO po) {
        return Category.reconstitute(
                po.getId(),
                po.getName(),
                po.getSlug(),
                po.getDescription(),
                po.getParentId(),
                po.getSortOrder(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }
}
