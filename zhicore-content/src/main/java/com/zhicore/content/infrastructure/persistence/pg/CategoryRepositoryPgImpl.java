package com.zhicore.content.infrastructure.persistence.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhicore.content.domain.model.Category;
import com.zhicore.content.domain.repository.CategoryRepository;
import com.zhicore.content.infrastructure.persistence.pg.entity.CategoryEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.CategoryEntityMapper;
import com.zhicore.content.infrastructure.persistence.pg.mapper.CategoryEntityMyBatisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL Category Repository 实现
 * 
 * 实现 CategoryRepository 端口接口，使用 MyBatis-Plus 访问 PostgreSQL。
 * 负责 Category 聚合根的持久化和查询。
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CategoryRepositoryPgImpl implements CategoryRepository {

    private final CategoryEntityMyBatisMapper mybatisMapper;
    private final CategoryEntityMapper entityMapper;

    @Override
    public void save(Category category) {
        CategoryEntity entity = entityMapper.toEntity(category);
        
        int rows = mybatisMapper.insert(entity);
        
        if (rows == 0) {
            throw new RuntimeException("保存分类失败: " + category.getId());
        }
        
        log.info("保存分类成功: id={}, name={}, slug={}", 
                category.getId(), category.getName(), category.getSlug());
    }

    @Override
    public void update(Category category) {
        CategoryEntity entity = entityMapper.toEntity(category);
        
        int rows = mybatisMapper.updateById(entity);
        
        if (rows == 0) {
            throw new RuntimeException("更新分类失败，分类可能不存在: " + category.getId());
        }
        
        log.debug("更新分类成功: id={}, name={}, slug={}", 
                category.getId(), category.getName(), category.getSlug());
    }

    @Override
    public Optional<Category> findById(String id) {
        CategoryEntity entity = mybatisMapper.selectById(Long.valueOf(id));
        return Optional.ofNullable(entity)
                .map(entityMapper::toDomain);
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        LambdaQueryWrapper<CategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryEntity::getSlug, slug);
        
        CategoryEntity entity = mybatisMapper.selectOne(wrapper);
        return Optional.ofNullable(entity)
                .map(entityMapper::toDomain);
    }

    @Override
    public List<Category> findAll() {
        LambdaQueryWrapper<CategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(CategoryEntity::getSortOrder)
                .orderByAsc(CategoryEntity::getName);
        
        List<CategoryEntity> entities = mybatisMapper.selectList(wrapper);
        return entityMapper.toDomainList(entities);
    }

    @Override
    public List<Category> findTopLevel() {
        List<CategoryEntity> entities = mybatisMapper.selectTopLevel();
        return entityMapper.toDomainList(entities);
    }

    @Override
    public List<Category> findByParentId(String parentId) {
        if (parentId == null) {
            return Collections.emptyList();
        }
        
        List<CategoryEntity> entities = mybatisMapper.selectByParentId(Long.valueOf(parentId));
        return entityMapper.toDomainList(entities);
    }

    @Override
    public void delete(String id) {
        int rows = mybatisMapper.deleteById(Long.valueOf(id));
        
        if (rows == 0) {
            log.warn("删除分类失败，分类可能不存在: {}", id);
        } else {
            log.info("删除分类成功: id={}", id);
        }
    }

    @Override
    public boolean existsBySlug(String slug) {
        LambdaQueryWrapper<CategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryEntity::getSlug, slug);
        
        return mybatisMapper.selectCount(wrapper) > 0;
    }
}
