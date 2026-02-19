package com.blog.post.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.post.domain.model.Tag;
import com.blog.post.domain.repository.TagRepository;
import com.blog.post.infrastructure.repository.mapper.TagMapper;
import com.blog.post.infrastructure.repository.po.TagPO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 标签仓储实现
 * 
 * 使用 MyBatis-Plus 实现 Tag 的持久化操作
 *
 * @author Blog Team
 */
@Repository
@RequiredArgsConstructor
public class TagRepositoryImpl implements TagRepository {

    private final TagMapper tagMapper;

    @Override
    public Tag save(Tag tag) {
        TagPO po = toPO(tag);
        
        // 检查是否已存在（通过 ID）
        TagPO existing = tagMapper.selectById(po.getId());
        if (existing != null) {
            // 更新
            tagMapper.updateById(po);
        } else {
            // 插入
            tagMapper.insert(po);
        }
        
        return tag;
    }

    @Override
    public Optional<Tag> findById(Long id) {
        TagPO po = tagMapper.selectById(id);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<Tag> findBySlug(String slug) {
        LambdaQueryWrapper<TagPO> wrapper = new LambdaQueryWrapper<TagPO>()
                .eq(TagPO::getSlug, slug);
        TagPO po = tagMapper.selectOne(wrapper);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<Tag> findBySlugIn(List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return List.of();
        }
        
        LambdaQueryWrapper<TagPO> wrapper = new LambdaQueryWrapper<TagPO>()
                .in(TagPO::getSlug, slugs);
        return tagMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Tag> findByIdIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        
        LambdaQueryWrapper<TagPO> wrapper = new LambdaQueryWrapper<TagPO>()
                .in(TagPO::getId, ids);
        return tagMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsBySlug(String slug) {
        LambdaQueryWrapper<TagPO> wrapper = new LambdaQueryWrapper<TagPO>()
                .eq(TagPO::getSlug, slug);
        return tagMapper.selectCount(wrapper) > 0;
    }

    @Override
    public org.springframework.data.domain.Page<Tag> findAll(Pageable pageable) {
        // 转换 Spring Data 的 Pageable 到 MyBatis-Plus 的 Page
        Page<TagPO> page = new Page<>(pageable.getPageNumber() + 1, pageable.getPageSize());
        
        LambdaQueryWrapper<TagPO> wrapper = new LambdaQueryWrapper<TagPO>()
                .orderByDesc(TagPO::getCreatedAt);
        
        IPage<TagPO> result = tagMapper.selectPage(page, wrapper);
        
        List<Tag> tags = result.getRecords().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
        
        return new PageImpl<>(tags, pageable, result.getTotal());
    }

    @Override
    public List<Tag> searchByName(String keyword, int limit) {
        LambdaQueryWrapper<TagPO> wrapper = new LambdaQueryWrapper<TagPO>()
                .like(TagPO::getName, keyword)
                .orderByDesc(TagPO::getCreatedAt)
                .last("LIMIT " + limit);
        
        return tagMapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<java.util.Map<String, Object>> findHotTags(int limit) {
        return tagMapper.findHotTags(limit);
    }

    @Override
    public void deleteById(Long id) {
        tagMapper.deleteById(id);
    }

    @Override
    public java.util.Map<Long, List<Tag>> findTagsByPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        // 批量查询标签（包含 post_id）
        List<java.util.Map<String, Object>> results = tagMapper.selectTagsByPostIds(postIds);

        // 按 postId 分组并转换为 Tag 对象
        return results.stream()
                .collect(Collectors.groupingBy(
                        map -> {
                            // 处理不同的键名格式（post_id 或 POST_ID）
                            Object postIdObj = map.get("post_id");
                            if (postIdObj == null) {
                                postIdObj = map.get("POST_ID");
                            }
                            return ((Number) postIdObj).longValue();
                        },
                        Collectors.mapping(this::mapToTag, Collectors.toList())
                ));
    }

    // ==================== 转换方法 ====================

    /**
     * Map 转 Tag 领域模型
     */
    private Tag mapToTag(java.util.Map<String, Object> map) {
        // 处理不同的键名格式（小写或大写）
        Object idObj = map.get("id");
        if (idObj == null) idObj = map.get("ID");
        
        Object nameObj = map.get("name");
        if (nameObj == null) nameObj = map.get("NAME");
        
        Object slugObj = map.get("slug");
        if (slugObj == null) slugObj = map.get("SLUG");
        
        Object descObj = map.get("description");
        if (descObj == null) descObj = map.get("DESCRIPTION");
        
        Object createdAtObj = map.get("created_at");
        if (createdAtObj == null) createdAtObj = map.get("CREATED_AT");
        
        Object updatedAtObj = map.get("updated_at");
        if (updatedAtObj == null) updatedAtObj = map.get("UPDATED_AT");
        
        // 转换 Timestamp 到 LocalDateTime
        LocalDateTime createdAt = convertToLocalDateTime(createdAtObj);
        LocalDateTime updatedAt = convertToLocalDateTime(updatedAtObj);
        
        return Tag.reconstitute(
                ((Number) idObj).longValue(),
                (String) nameObj,
                (String) slugObj,
                (String) descObj,
                createdAt,
                updatedAt
        );
    }
    
    /**
     * 转换对象到 LocalDateTime
     * 支持 java.sql.Timestamp 和 java.time.LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof LocalDateTime) {
            return (LocalDateTime) obj;
        }
        if (obj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) obj).toLocalDateTime();
        }
        throw new IllegalArgumentException("Cannot convert " + obj.getClass() + " to LocalDateTime");
    }

    /**
     * 领域模型转持久化对象
     */
    private TagPO toPO(Tag tag) {
        TagPO po = new TagPO();
        po.setId(tag.getId());
        po.setName(tag.getName());
        po.setSlug(tag.getSlug());
        po.setDescription(tag.getDescription());
        po.setCreatedAt(tag.getCreatedAt());
        po.setUpdatedAt(tag.getUpdatedAt());
        return po;
    }

    /**
     * 持久化对象转领域模型
     */
    private Tag toDomain(TagPO po) {
        return Tag.reconstitute(
                po.getId(),
                po.getName(),
                po.getSlug(),
                po.getDescription(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }
}
