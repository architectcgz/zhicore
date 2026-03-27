package com.zhicore.content.infrastructure.persistence.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhicore.content.domain.model.PostFavorite;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.domain.repository.PostFavoriteRepository;
import com.zhicore.content.infrastructure.persistence.pg.entity.PostFavoriteEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.PostFavoriteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文章收藏仓储实现（R3）
 *
 * 约束：
 * - (post_id, user_id) 唯一约束用于实现并发幂等；
 * - delete 0 行视为成功（幂等语义）。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostFavoriteRepositoryImpl implements PostFavoriteRepository {

    private final PostFavoriteMapper mapper;

    @Override
    public boolean save(PostFavorite favorite) {
        PostFavoriteEntity entity = new PostFavoriteEntity();
        entity.setId(favorite.getId());
        entity.setPostId(favorite.getPostId().getValue());
        entity.setUserId(favorite.getUserId().getValue());
        entity.setCreatedAt(favorite.getCreatedAt() != null ? favorite.getCreatedAt() : LocalDateTime.now());

        int inserted = mapper.insertIgnoreConflict(entity);
        if (inserted == 0) {
            log.info("Post favorite already exists, treat as idempotent success: postId={}, userId={}",
                    favorite.getPostId().getValue(), favorite.getUserId().getValue());
            return false;
        }
        return true;
    }

    @Override
    public boolean delete(PostId postId, UserId userId) {
        return mapper.deleteByPostIdAndUserId(postId.getValue(), userId.getValue()) > 0;
    }

    @Override
    public Optional<PostFavorite> findByPostIdAndUserId(PostId postId, UserId userId) {
        PostFavoriteEntity entity = mapper.selectOne(
                new LambdaQueryWrapper<PostFavoriteEntity>()
                        .eq(PostFavoriteEntity::getPostId, postId.getValue())
                        .eq(PostFavoriteEntity::getUserId, userId.getValue())
                        .last("LIMIT 1")
        );
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(PostFavorite.reconstitute(
                entity.getId(),
                PostId.of(entity.getPostId()),
                UserId.of(entity.getUserId()),
                entity.getCreatedAt()));
    }

    @Override
    public boolean exists(PostId postId, UserId userId) {
        return mapper.selectCount(
                new LambdaQueryWrapper<PostFavoriteEntity>()
                        .eq(PostFavoriteEntity::getPostId, postId.getValue())
                        .eq(PostFavoriteEntity::getUserId, userId.getValue())
        ) > 0;
    }

    @Override
    public List<PostFavorite> findByUserIdCursor(UserId userId, LocalDateTime cursor, int limit) {
        LambdaQueryWrapper<PostFavoriteEntity> wrapper = new LambdaQueryWrapper<PostFavoriteEntity>()
                .eq(PostFavoriteEntity::getUserId, userId.getValue())
                .orderByDesc(PostFavoriteEntity::getCreatedAt)
                .last("LIMIT " + limit);
        if (cursor != null) {
            wrapper.lt(PostFavoriteEntity::getCreatedAt, cursor);
        }

        return mapper.selectList(wrapper).stream()
                .map(e -> PostFavorite.reconstitute(
                        e.getId(),
                        PostId.of(e.getPostId()),
                        UserId.of(e.getUserId()),
                        e.getCreatedAt()))
                .toList();
    }

    @Override
    public int countByPostId(PostId postId) {
        return Math.toIntExact(mapper.selectCount(
                new LambdaQueryWrapper<PostFavoriteEntity>()
                        .eq(PostFavoriteEntity::getPostId, postId.getValue())
        ));
    }

    @Override
    public List<PostId> findFavoritedPostIds(UserId userId, List<PostId> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }

        List<Long> rawPostIds = postIds.stream().map(PostId::getValue).toList();
        QueryWrapper<PostFavoriteEntity> wrapper = new QueryWrapper<PostFavoriteEntity>()
                .select("post_id")
                .eq("user_id", userId.getValue())
                .in("post_id", rawPostIds);

        return mapper.selectList(wrapper).stream()
                .map(PostFavoriteEntity::getPostId)
                .map(PostId::of)
                .toList();
    }
}
