package com.zhicore.content.infrastructure.persistence.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhicore.common.util.DateTimeUtils;
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
            // MyBatis 在不同数据库/驱动下可能返回不同的列名大小写（例如 POST_ID vs post_id）
            Long postId = (Long) getIgnoreCase(row, "post_id");
            
            // 构建 TagEntity
            TagEntity entity = new TagEntity();
            entity.setId((Long) getIgnoreCase(row, "id"));
            entity.setName((String) getIgnoreCase(row, "name"));
            entity.setSlug((String) getIgnoreCase(row, "slug"));
            entity.setDescription((String) getIgnoreCase(row, "description"));
            entity.setCreatedAt(toOffsetDateTime(getIgnoreCase(row, "created_at")));
            entity.setUpdatedAt(toOffsetDateTime(getIgnoreCase(row, "updated_at")));
            
            Tag tag = entityMapper.toDomain(entity);
            
            tagsByPostId.computeIfAbsent(postId, k -> new ArrayList<>()).add(tag);
        }
        
        return tagsByPostId;
    }

    private static Object getIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static java.time.OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof java.time.OffsetDateTime localDateTime) {
            return localDateTime;
        }

        if (value instanceof java.sql.Timestamp timestamp) {
            return DateTimeUtils.toOffsetDateTime(timestamp);
        }

        if (value instanceof java.util.Date date) {
            return java.time.OffsetDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
        }

        throw new IllegalArgumentException("不支持的时间类型: " + value.getClass().getName());
    }
}
