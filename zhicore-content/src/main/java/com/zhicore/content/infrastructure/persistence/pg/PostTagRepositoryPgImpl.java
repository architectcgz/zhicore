package com.zhicore.content.infrastructure.persistence.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostTagEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostTagEntityMyBatisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PostgreSQL PostTag Repository 实现
 * 
 * 实现 PostTagRepository 接口，使用 MyBatis-Plus 访问 PostgreSQL。
 * 负责文章与标签的多对多关联关系管理。
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostTagRepositoryPgImpl implements PostTagRepository {

    private final PostTagEntityMyBatisMapper mybatisMapper;

    @Override
    public void attach(PostId postId, TagId tagId) {
        // 检查关联是否已存在
        if (exists(postId, tagId)) {
            log.debug("文章-标签关联已存在: postId={}, tagId={}", postId, tagId);
            return;
        }

        PostTagEntity entity = new PostTagEntity();
        entity.setPostId(postId.getValue());
        entity.setTagId(tagId.getValue());
        entity.setCreatedAt(OffsetDateTime.now());

        int rows = mybatisMapper.insertOneIgnoreConflict(entity);
        
        if (rows > 0) {
            log.info("创建文章-标签关联成功: postId={}, tagId={}", postId, tagId);
        } else {
            log.warn("创建文章-标签关联失败: postId={}, tagId={}", postId, tagId);
        }
    }

    @Override
    public void attachBatch(PostId postId, List<TagId> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            log.debug("标签ID列表为空，跳过批量创建关联: postId={}", postId);
            return;
        }

        // 过滤掉已存在的关联
        List<TagId> existingTagIds = findTagIdsByPostId(postId);
        List<TagId> newTagIds = tagIds.stream()
                .filter(tagId -> !existingTagIds.contains(tagId))
                .collect(Collectors.toList());

        if (newTagIds.isEmpty()) {
            log.debug("所有标签关联已存在，跳过批量创建: postId={}", postId);
            return;
        }

        // 批量插入
        OffsetDateTime now = OffsetDateTime.now();
        List<PostTagEntity> entities = newTagIds.stream()
                .map(tagId -> {
                    PostTagEntity entity = new PostTagEntity();
                    entity.setPostId(postId.getValue());
                    entity.setTagId(tagId.getValue());
                    entity.setCreatedAt(now);
                    return entity;
                })
                .collect(Collectors.toList());

        int successCount = mybatisMapper.insertBatchIgnoreConflict(entities);

        log.info("批量创建文章-标签关联完成: postId={}, 成功={}/{}", 
                postId, successCount, entities.size());
    }

    @Override
    public void detach(PostId postId, TagId tagId) {
        LambdaQueryWrapper<PostTagEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostTagEntity::getPostId, postId.getValue())
               .eq(PostTagEntity::getTagId, tagId.getValue());

        int rows = mybatisMapper.delete(wrapper);
        
        if (rows > 0) {
            log.info("删除文章-标签关联成功: postId={}, tagId={}", postId, tagId);
        } else {
            log.debug("文章-标签关联不存在: postId={}, tagId={}", postId, tagId);
        }
    }

    @Override
    public void detachAllByPostId(PostId postId) {
        int rows = mybatisMapper.deleteByPostId(postId.getValue());
        
        log.info("删除文章的所有标签关联: postId={}, 删除数量={}", postId, rows);
    }

    @Override
    public List<TagId> findTagIdsByPostId(PostId postId) {
        return mybatisMapper.selectTagIdsByPostId(postId.getValue()).stream()
                .map(TagId::of)
                .toList();
    }

    @Override
    public List<PostId> findPostIdsByTagId(TagId tagId) {
        return mybatisMapper.selectPostIdsByTagId(tagId.getValue()).stream()
                .map(PostId::of)
                .toList();
    }

    @Override
    public org.springframework.data.domain.Page<PostId> findPostIdsByTagId(TagId tagId, Pageable pageable) {
        // 使用 MyBatis-Plus 分页
        Page<PostTagEntity> page = new Page<>(
                pageable.getPageNumber() + 1,  // MyBatis-Plus 页码从1开始
                pageable.getPageSize()
        );

        LambdaQueryWrapper<PostTagEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostTagEntity::getTagId, tagId.getValue())
               .orderByDesc(PostTagEntity::getCreatedAt);

        Page<PostTagEntity> result = mybatisMapper.selectPage(page, wrapper);

        List<PostId> postIds = result.getRecords().stream()
                .map(PostTagEntity::getPostId)
                .map(PostId::of)
                .collect(Collectors.toList());

        return new PageImpl<>(postIds, pageable, result.getTotal());
    }

    @Override
    public boolean exists(PostId postId, TagId tagId) {
        int count = mybatisMapper.existsByPostIdAndTagId(postId.getValue(), tagId.getValue());
        return count > 0;
    }

    @Override
    public int countPostsByTagId(TagId tagId) {
        return mybatisMapper.countPostsByTagId(tagId.getValue());
    }

    @Override
    public int countTagsByPostId(PostId postId) {
        return mybatisMapper.countTagsByPostId(postId.getValue());
    }

    @Override
    public Map<PostId, List<TagId>> findTagIdsByPostIds(List<PostId> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new HashMap<>();
        }

        List<Long> rawPostIds = postIds.stream().map(PostId::getValue).toList();
        List<PostTagEntity> entities = mybatisMapper.selectByPostIds(rawPostIds);

        // 按 postId 分组
        return entities.stream()
                .collect(Collectors.groupingBy(
                        entity -> PostId.of(entity.getPostId()),
                        Collectors.mapping(entity -> TagId.of(entity.getTagId()), Collectors.toList())
                ));
    }
}
