package com.zhicore.content.infrastructure.persistence.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhicore.content.domain.model.PostLike;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.repository.PostLikeRepository;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostLikeEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostLikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public boolean save(PostLike like) {
        PostLikeEntity entity = new PostLikeEntity();
        entity.setId(like.getId());
        entity.setPostId(like.getPostId().getValue());
        entity.setUserId(like.getUserId().getValue());
        entity.setCreatedAt(like.getCreatedAt() != null ? like.getCreatedAt() : LocalDateTime.now());

        int inserted = mapper.insertIgnoreConflict(entity);
        if (inserted == 0) {
            log.info("Post like already exists, treat as idempotent success: postId={}, userId={}",
                    like.getPostId().getValue(), like.getUserId().getValue());
            return false;
        }
        return true;
    }

    @Override
    public boolean delete(PostId postId, UserId userId) {
        return mapper.deleteByPostIdAndUserId(postId.getValue(), userId.getValue()) > 0;
    }

    @Override
    public Optional<PostLike> findByPostIdAndUserId(PostId postId, UserId userId) {
        PostLikeEntity entity = mapper.selectOne(
                new LambdaQueryWrapper<PostLikeEntity>()
                        .eq(PostLikeEntity::getPostId, postId.getValue())
                        .eq(PostLikeEntity::getUserId, userId.getValue())
                        .last("LIMIT 1")
        );
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(PostLike.reconstitute(
                entity.getId(),
                PostId.of(entity.getPostId()),
                UserId.of(entity.getUserId()),
                entity.getCreatedAt()));
    }

    @Override
    public boolean exists(PostId postId, UserId userId) {
        return mapper.selectCount(
                new LambdaQueryWrapper<PostLikeEntity>()
                        .eq(PostLikeEntity::getPostId, postId.getValue())
                        .eq(PostLikeEntity::getUserId, userId.getValue())
        ) > 0;
    }

    @Override
    public List<PostLike> findByUserIdCursor(UserId userId, LocalDateTime cursor, int limit) {
        LambdaQueryWrapper<PostLikeEntity> wrapper = new LambdaQueryWrapper<PostLikeEntity>()
                .eq(PostLikeEntity::getUserId, userId.getValue())
                .orderByDesc(PostLikeEntity::getCreatedAt)
                .last("LIMIT " + limit);
        if (cursor != null) {
            wrapper.lt(PostLikeEntity::getCreatedAt, cursor);
        }

        return mapper.selectList(wrapper).stream()
                .map(e -> PostLike.reconstitute(
                        e.getId(),
                        PostId.of(e.getPostId()),
                        UserId.of(e.getUserId()),
                        e.getCreatedAt()))
                .toList();
    }

    @Override
    public int countByPostId(PostId postId) {
        return Math.toIntExact(mapper.selectCount(
                new LambdaQueryWrapper<PostLikeEntity>()
                        .eq(PostLikeEntity::getPostId, postId.getValue())
        ));
    }

    @Override
    public List<PostId> findLikedPostIds(UserId userId, List<PostId> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }

        List<Long> rawPostIds = postIds.stream().map(PostId::getValue).toList();
        QueryWrapper<PostLikeEntity> wrapper = new QueryWrapper<PostLikeEntity>()
                .select("post_id")
                .eq("user_id", userId.getValue())
                .in("post_id", rawPostIds);

        return mapper.selectList(wrapper).stream()
                .map(PostLikeEntity::getPostId)
                .map(PostId::of)
                .toList();
    }
}
