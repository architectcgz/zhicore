package com.zhicore.content.infrastructure.persistence.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.infrastructure.persistence.pg.entity.TagEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.TagEntityMapper;
import com.zhicore.content.infrastructure.persistence.pg.mapper.TagEntityMyBatisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PostgreSQL Tag Repository 实现
 * 
 * 实现 TagRepository 端口接口，使用 MyBatis-Plus 访问 PostgreSQL。
 * 负责 Tag 聚合根的持久化和查询。
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TagRepositoryPgImpl implements TagRepository {

    private final TagEntityMyBatisMapper mybatisMapper;
    private final TagEntityMapper entityMapper;

    @Override
    public Tag save(Tag tag) {
        TagEntity entity = entityMapper.toEntity(tag);
        
        // 检查是否已存在（通过 ID）
        TagEntity existing = mybatisMapper.selectById(entity.getId());
        
        int rows;
        if (existing == null) {
            // 插入新记录
            rows = mybatisMapper.insert(entity);
            log.info("保存标签成功: id={}, name={}, slug={}", 
                    tag.getId(), tag.getName(), tag.getSlug());
        } else {
            // 更新现有记录
            rows = mybatisMapper.updateById(entity);
            log.debug("更新标签成功: id={}, name={}, slug={}", 
                    tag.getId(), tag.getName(), tag.getSlug());
        }
        
        if (rows == 0) {
            throw new RuntimeException("保存标签失败: " + tag.getId());
        }
        
        return tag;
    }

    @Override
    public Optional<Tag> findById(Long id) {
        TagEntity entity = mybatisMapper.selectById(id);
        return Optional.ofNullable(entity)
                .map(entityMapper::toDomain);
    }

    @Override
    public Optional<Tag> findBySlug(String slug) {
        LambdaQueryWrapper<TagEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TagEntity::getSlug, slug);
        
        TagEntity entity = mybatisMapper.selectOne(wrapper);
        return Optional.ofNullable(entity)
                .map(entityMapper::toDomain);
    }

    @Override
    public List<Tag> findBySlugIn(List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<TagEntity> entities = mybatisMapper.selectBySlugIn(slugs);
        return entityMapper.toDomainList(entities);
    }

    @Override
    public List<Tag> findByIdIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<TagEntity> entities = mybatisMapper.selectBatchIds(ids);
        return entityMapper.toDomainList(entities);
    }

    @Override
    public boolean existsBySlug(String slug) {
        LambdaQueryWrapper<TagEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TagEntity::getSlug, slug);
        
        return mybatisMapper.selectCount(wrapper) > 0;
    }

    @Override
    public org.springframework.data.domain.Page<Tag> findAll(Pageable pageable) {
        Page<TagEntity> page = new Page<>(
                pageable.getPageNumber() + 1,  // MyBatis-Plus 页码从 1 开始
                pageable.getPageSize()
        );
        
        LambdaQueryWrapper<TagEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(TagEntity::getCreatedAt);
        
        Page<TagEntity> result = mybatisMapper.selectPage(page, wrapper);
        
        List<Tag> tags = entityMapper.toDomainList(result.getRecords());
        
        return new org.springframework.data.domain.PageImpl<>(
                tags,
                pageable,
                result.getTotal()
        );
    }

    @Override
    public List<Tag> searchByName(String keyword, int limit) {
        List<TagEntity> entities = mybatisMapper.searchByName(keyword, limit);
        return entityMapper.toDomainList(entities);
    }

    @Override
    public List<Map<String, Object>> findHotTags(int limit) {
        return mybatisMapper.selectHotTags(limit);
    }

    @Override
    public void deleteById(Long id) {
        int rows = mybatisMapper.deleteById(id);
        
        if (rows == 0) {
            log.warn("删除标签失败，标签可能不存在: {}", id);
        } else {
            log.info("删除标签成功: id={}", id);
        }
    }

    @Override
    public Map<Long, List<Tag>> findTagsByPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        List<Map<String, Object>> results = mybatisMapper.selectTagsByPostIds(postIds);
        
        // 将结果转换为 Map<postId, List<Tag>>
        Map<Long, List<Tag>> tagsByPostId = new HashMap<>();
        
        for (Map<String, Object> row : results) {
            Long postId = (Long) row.get("post_id");
            
            // 构建 TagEntity
            TagEntity entity = new TagEntity();
            entity.setId((Long) row.get("id"));
            entity.setName((String) row.get("name"));
            entity.setSlug((String) row.get("slug"));
            entity.setDescription((String) row.get("description"));
            entity.setCreatedAt((java.time.LocalDateTime) row.get("created_at"));
            entity.setUpdatedAt((java.time.LocalDateTime) row.get("updated_at"));
            
            Tag tag = entityMapper.toDomain(entity);
            
            tagsByPostId.computeIfAbsent(postId, k -> new ArrayList<>()).add(tag);
        }
        
        return tagsByPostId;
    }
}
