package com.zhicore.content.infrastructure.persistence.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhicore.content.domain.model.PostLike;
import com.zhicore.content.domain.repository.PostLikeRepository;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostLikeEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostLikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文章点赞仓储实现（R2）
 *
 * 约束：
 * - (post_id, user_id) 唯一约束用于实现并发幂等；
 * - delete 0 行视为成功（幂等语义）。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostLikeRepositoryImpl implements PostLikeRepository {

    private final PostLikeMapper mapper;

    @Override
    public void save(PostLike like) {
        try {
            PostLikeEntity entity = new PostLikeEntity();
            entity.setId(like.getId());
            entity.setPostId(like.getPostId());
            entity.setUserId(like.getUserId());
            entity.setCreatedAt(like.getCreatedAt() != null ? like.getCreatedAt() : LocalDateTime.now());
            mapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 唯一约束冲突视为幂等成功（并发下可能重复提交）
            log.info("Post like already exists, treat as idempotent success: postId={}, userId={}",
                    like.getPostId(), like.getUserId());
        }
    }

    @Override
    public void delete(Long postId, Long userId) {
        mapper.delete(
                new LambdaQueryWrapper<PostLikeEntity>()
                        .eq(PostLikeEntity::getPostId, postId)
                        .eq(PostLikeEntity::getUserId, userId)
        );
    }

    @Override
    public Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId) {
        PostLikeEntity entity = mapper.selectOne(
                new LambdaQueryWrapper<PostLikeEntity>()
                        .eq(PostLikeEntity::getPostId, postId)
                        .eq(PostLikeEntity::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(PostLike.reconstitute(entity.getId(), entity.getPostId(), entity.getUserId(), entity.getCreatedAt()));
    }

    @Override
    public boolean exists(Long postId, Long userId) {
        return mapper.selectCount(
                new LambdaQueryWrapper<PostLikeEntity>()
                        .eq(PostLikeEntity::getPostId, postId)
                        .eq(PostLikeEntity::getUserId, userId)
        ) > 0;
    }

    @Override
    public List<PostLike> findByUserIdCursor(Long userId, LocalDateTime cursor, int limit) {
        LambdaQueryWrapper<PostLikeEntity> wrapper = new LambdaQueryWrapper<PostLikeEntity>()
                .eq(PostLikeEntity::getUserId, userId)
                .orderByDesc(PostLikeEntity::getCreatedAt)
                .last("LIMIT " + limit);
        if (cursor != null) {
            wrapper.lt(PostLikeEntity::getCreatedAt, cursor);
        }

        return mapper.selectList(wrapper).stream()
                .map(e -> PostLike.reconstitute(e.getId(), e.getPostId(), e.getUserId(), e.getCreatedAt()))
                .toList();
    }

    @Override
    public int countByPostId(Long postId) {
        return Math.toIntExact(mapper.selectCount(
                new LambdaQueryWrapper<PostLikeEntity>()
                        .eq(PostLikeEntity::getPostId, postId)
        ));
    }

    @Override
    public List<Long> findLikedPostIds(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }

        QueryWrapper<PostLikeEntity> wrapper = new QueryWrapper<PostLikeEntity>()
                .select("post_id")
                .eq("user_id", userId)
                .in("post_id", postIds);

        return mapper.selectList(wrapper).stream()
                .map(PostLikeEntity::getPostId)
                .toList();
    }
}

