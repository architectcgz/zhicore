package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.common.exception.DomainException;
import com.zhicore.common.exception.OptimisticLockException;
import com.zhicore.common.exception.ResourceNotFoundException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostEntity;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostTagEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostEntityMapper;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostEntityMyBatisMapper;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostTagEntityMyBatisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PostgreSQL Post Repository 实现
 * 
 * 实现 PostRepository 端口接口，使用 MyBatis-Plus 访问 PostgreSQL。
 * 负责 Post 聚合根的持久化和查询。
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostRepositoryPgImpl implements PostRepository {

    private final PostEntityMyBatisMapper mybatisMapper;
    private final PostEntityMapper entityMapper;
    private final PostTagEntityMyBatisMapper postTagMapper;

    @Override
    public Post load(PostId id) {
        return findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("文章不存在: " + id.getValue()));
    }

    @Override
    public Optional<Post> findById(PostId id) {
        // 值对象转 Long 用于数据库查询
        PostEntity entity = mybatisMapper.selectById(id.getValue());
        return Optional.ofNullable(entity)
                .map(entityMapper::toDomain);
    }

    @Override
    public Optional<Post> findById(Long id) {
        return findById(PostId.of(id));
    }

    @Override
    public PostId save(Post post) {
        PostEntity entity = entityMapper.toEntity(post);
        int rows = mybatisMapper.insert(entity);
        
        if (rows == 0) {
            throw new DomainException("保存文章失败: " + post.getId().getValue());
        }
        
        // 保存标签关联
        saveTagAssociations(post.getId(), post.getTagIds());
        
        log.info("保存文章成功: id={}, title={}, writeState={}", 
                post.getId().getValue(), post.getTitle(), post.getWriteState());
        
        return post.getId();
    }

    @Override
    public void update(Post post) {
        PostEntity entity = entityMapper.toEntity(post);
        int rows = mybatisMapper.updateById(entity);
        
        if (rows == 0) {
            PostEntity existing = mybatisMapper.selectById(post.getId().getValue());
            if (existing == null) {
                throw new ResourceNotFoundException("文章不存在");
            }
            throw OptimisticLockException.concurrentUpdateConflict();
        }
        
        // 更新标签关联
        updateTagAssociations(post.getId(), post.getTagIds());
        
        log.debug("更新文章成功: id={}, title={}, writeState={}", 
                post.getId().getValue(), post.getTitle(), post.getWriteState());
    }

    @Override
    public void delete(PostId id) {
        // 值对象转 Long
        int rows = mybatisMapper.deleteById(id.getValue());
        
        if (rows == 0) {
            log.warn("删除文章失败，文章可能不存在: {}", id.getValue());
        } else {
            // 只删除当前文章的标签关联，避免误删全表
            postTagMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PostTagEntity>()
                    .eq("post_id", id.getValue())
            );
            log.info("删除文章成功: id={}", id.getValue());
        }
    }

    @Override
    public List<Post> findByAuthor(UserId authorId, Pageable pageable) {
        long offset = pageable.getOffset();
        int limit = pageable.getPageSize();
        
        // 值对象转 Long
        List<PostEntity> entities = mybatisMapper.selectByAuthor(authorId.getValue(), offset, limit);
        
        return entities.stream()
                .map(entityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Post> findByTag(TagId tagId, Pageable pageable) {
        long offset = pageable.getOffset();
        int limit = pageable.getPageSize();
        
        // 值对象转 Long
        List<PostEntity> entities = mybatisMapper.selectByTag(tagId.getValue(), offset, limit);
        
        return entities.stream()
                .map(entityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Post> findLatest(Pageable pageable) {
        long offset = pageable.getOffset();
        int limit = pageable.getPageSize();
        
        List<PostEntity> entities = mybatisMapper.selectLatest(offset, limit);
        
        return entities.stream()
                .map(entityMapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * 根据写入状态查询文章列表
     * 
     * 用于清理任务，查询标记为 INCOMPLETE 的文章。
     * 此方法不在 PostRepository 接口中，是基础设施层的扩展方法。
     * 
     * @param writeState 写入状态
     * @return 文章列表
     */
    public List<Post> findByWriteState(String writeState) {
        List<PostEntity> entities = mybatisMapper.selectByWriteState(writeState);
        
        return entities.stream()
                .map(entityMapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * 批量更新作者信息
     * 
     * 用于处理用户资料更新事件。
     * 此方法不在 PostRepository 接口中，是基础设施层的扩展方法。
     * 
     * @param userId 用户ID（值对象）
     * @param nickname 新的昵称
     * @param avatarId 新的头像文件ID
     * @param version 新的版本号
     * @return 更新的文章数量
     */
    public int updateAuthorInfo(UserId userId, String nickname, String avatarId, Long version) {
        // 值对象转 Long
        int count = mybatisMapper.updateAuthorInfo(userId.getValue(), nickname, avatarId, version);
        
        log.info("批量更新作者信息: userId={}, nickname={}, version={}, updatedCount={}", 
                userId.getValue(), nickname, version, count);
        
        return count;
    }

    @Override
    public int updateAuthorInfo(Long userId, String nickname, String avatarId, Long version) {
        return updateAuthorInfo(UserId.of(userId), nickname, avatarId, version);
    }

    @Override
    public Map<Long, Post> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        List<PostEntity> entities = mybatisMapper.selectBatchIds(ids);
        Map<Long, Post> result = new LinkedHashMap<>();
        for (PostEntity entity : entities) {
            Post post = entityMapper.toDomain(entity);
            result.put(entity.getId(), post);
        }
        return result;
    }

    @Override
    public List<Post> findByOwnerId(Long ownerId, PostStatus status, int offset, int limit) {
        LambdaQueryWrapper<PostEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostEntity::getOwnerId, ownerId)
                .orderByDesc(PostEntity::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset);
        if (status != null) {
            wrapper.eq(PostEntity::getStatus, status.getCode());
        } else {
            wrapper.ne(PostEntity::getStatus, PostStatus.DELETED.getCode());
        }

        return mybatisMapper.selectList(wrapper).stream()
                .map(entityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Post> findPublished(int offset, int limit) {
        return mybatisMapper.selectList(
                new LambdaQueryWrapper<PostEntity>()
                        .eq(PostEntity::getStatus, PostStatus.PUBLISHED.getCode())
                        .orderByDesc(PostEntity::getPublishedAt)
                        .orderByDesc(PostEntity::getId)
                        .last("LIMIT " + limit + " OFFSET " + offset)
        ).stream().map(entityMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Post> findPublishedCursor(OffsetDateTime cursorPublishedAt, Long cursorPostId, int limit) {
        // 兼容：如果只传了时间游标但没有 id，则跳过同一时间戳的记录，避免重复
        Long safeCursorId = cursorPublishedAt == null ? null : (cursorPostId == null ? 0L : cursorPostId);

        return mybatisMapper.selectPublishedCursor(cursorPublishedAt, safeCursorId, limit).stream()
                .map(entityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Post> findPublishedPopular(int offset, int limit) {
        return mybatisMapper.selectPublishedPopular(offset, limit).stream()
                .map(entityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Post> findPublishedByAuthor(Long authorId, int offset, int limit) {
        return mybatisMapper.selectList(
                new LambdaQueryWrapper<PostEntity>()
                        .eq(PostEntity::getOwnerId, authorId)
                        .eq(PostEntity::getStatus, PostStatus.PUBLISHED.getCode())
                        .orderByDesc(PostEntity::getPublishedAt)
                        .orderByDesc(PostEntity::getId)
                        .last("LIMIT " + limit + " OFFSET " + offset)
        ).stream().map(entityMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countPublishedByAuthor(Long authorId) {
        return mybatisMapper.selectCount(
                new LambdaQueryWrapper<PostEntity>()
                        .eq(PostEntity::getOwnerId, authorId)
                        .eq(PostEntity::getStatus, PostStatus.PUBLISHED.getCode())
        );
    }

    @Override
    public Optional<Long> publishScheduledIfNeeded(Long postId, OffsetDateTime publishedAt) {
        Long version = mybatisMapper.publishScheduledIfNeeded(postId, publishedAt);
        return Optional.ofNullable(version);
    }

    @Override
    public long countPublished() {
        return mybatisMapper.selectCount(
                new LambdaQueryWrapper<PostEntity>()
                        .eq(PostEntity::getStatus, PostStatus.PUBLISHED.getCode())
        );
    }

    @Override
    public List<Post> findByConditions(String keyword, String status, Long authorId, int offset, int limit) {
        LambdaQueryWrapper<PostEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(PostEntity::getTitle, keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(PostEntity::getStatus, Integer.parseInt(status));
        }
        if (authorId != null) {
            wrapper.eq(PostEntity::getOwnerId, authorId);
        }

        wrapper.orderByDesc(PostEntity::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset);

        return mybatisMapper.selectList(wrapper).stream()
                .map(entityMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countByConditions(String keyword, String status, Long authorId) {
        LambdaQueryWrapper<PostEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(PostEntity::getTitle, keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(PostEntity::getStatus, Integer.parseInt(status));
        }
        if (authorId != null) {
            wrapper.eq(PostEntity::getOwnerId, authorId);
        }
        return mybatisMapper.selectCount(wrapper);
    }

    @Override
    public List<Post> findByOwnerNameAndVersion(String ownerName, Long version, int limit) {
        return mybatisMapper.selectList(
                new LambdaQueryWrapper<PostEntity>()
                        .eq(PostEntity::getOwnerName, ownerName)
                        .lt(PostEntity::getOwnerProfileVersion, version)
                        .orderByAsc(PostEntity::getUpdatedAt)
                        .last("LIMIT " + limit)
        ).stream().map(entityMapper::toDomain).collect(Collectors.toList());
    }

    /**
     * 保存标签关联
     * 
     * @param postId 文章ID（值对象）
     * @param tagIds 标签ID集合（值对象）
     */
    private void saveTagAssociations(PostId postId, Set<TagId> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        
        // 转换为 Entity 列表
        List<PostTagEntity> postTagList = tagIds.stream()
                .map(tagId -> {
                    PostTagEntity entity = new PostTagEntity();
                    entity.setPostId(postId.getValue());  // 值对象转 Long
                    entity.setTagId(tagId.getValue());    // 值对象转 Long
                    entity.setCreatedAt(OffsetDateTime.now());
                    return entity;
                })
                .collect(Collectors.toList());
        
        // 批量插入
        if (!postTagList.isEmpty()) {
            postTagMapper.insertBatchIgnoreConflict(postTagList);
            log.debug("保存标签关联: postId={}, tagCount={}", postId.getValue(), postTagList.size());
        }
    }

    /**
     * 更新标签关联（先删除再插入）
     * 
     * @param postId 文章ID（值对象）
     * @param tagIds 标签ID集合（值对象）
     */
    private void updateTagAssociations(PostId postId, Set<TagId> tagIds) {
        Set<Long> newTagIdValues = (tagIds == null || tagIds.isEmpty())
                ? java.util.Set.of()
                : tagIds.stream().map(TagId::getValue).collect(java.util.stream.Collectors.toSet());

        // 当前已有的标签关联
        List<Long> existingValues = postTagMapper.selectTagIdsByPostId(postId.getValue());
        java.util.Set<Long> existingSet = existingValues == null
                ? new java.util.HashSet<>()
                : new java.util.HashSet<>(existingValues);

        // 差量计算：attach/detach（R18）
        java.util.Set<Long> toDetach = new java.util.HashSet<>(existingSet);
        toDetach.removeAll(newTagIdValues);

        java.util.Set<Long> toAttach = new java.util.HashSet<>(newTagIdValues);
        toAttach.removeAll(existingSet);

        if (!toDetach.isEmpty()) {
            postTagMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PostTagEntity>()
                            .eq("post_id", postId.getValue())
                            .in("tag_id", toDetach)
            );
        }

        if (!toAttach.isEmpty()) {
            saveTagAssociations(postId, toAttach.stream().map(TagId::of).collect(java.util.stream.Collectors.toSet()));
        }

        log.debug("更新标签关联（差量）: postId={}, attachCount={}, detachCount={}, newTagCount={}",
                postId.getValue(),
                toAttach.size(),
                toDetach.size(),
                newTagIdValues.size());
    }

    /**
     * 查询文章的标签ID列表
     * 
     * @param postId 文章ID
     * @return 标签ID集合（值对象）
     */
    Set<TagId> queryTagIds(Long postId) {
        List<Long> tagIdValues = postTagMapper.selectTagIdsByPostId(postId);
        return tagIdValues.stream()
                .map(TagId::of)  // Long 转值对象
                .collect(Collectors.toSet());
    }
}
