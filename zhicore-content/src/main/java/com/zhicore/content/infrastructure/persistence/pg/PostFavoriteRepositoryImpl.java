package com.zhicore.content.infrastructure.persistence.pg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhicore.content.domain.model.PostFavorite;
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
        entity.setPostId(favorite.getPostId());
        entity.setUserId(favorite.getUserId());
        entity.setCreatedAt(favorite.getCreatedAt() != null ? favorite.getCreatedAt() : LocalDateTime.now());

        int inserted = mapper.insertIgnoreConflict(entity);
        if (inserted == 0) {
            log.info("Post favorite already exists, treat as idempotent success: postId={}, userId={}",
                    favorite.getPostId(), favorite.getUserId());
            return false;
        }
        return true;
    }

    @Override
    public boolean delete(Long postId, Long userId) {
        return mapper.deleteByPostIdAndUserId(postId, userId) > 0;
    }

    @Override
    public Optional<PostFavorite> findByPostIdAndUserId(Long postId, Long userId) {
        PostFavoriteEntity entity = mapper.selectOne(
                new LambdaQueryWrapper<PostFavoriteEntity>()
                        .eq(PostFavoriteEntity::getPostId, postId)
                        .eq(PostFavoriteEntity::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(PostFavorite.reconstitute(entity.getId(), entity.getPostId(), entity.getUserId(), entity.getCreatedAt()));
    }

    @Override
    public boolean exists(Long postId, Long userId) {
        return mapper.selectCount(
                new LambdaQueryWrapper<PostFavoriteEntity>()
                        .eq(PostFavoriteEntity::getPostId, postId)
                        .eq(PostFavoriteEntity::getUserId, userId)
        ) > 0;
    }

    @Override
    public List<PostFavorite> findByUserIdCursor(Long userId, LocalDateTime cursor, int limit) {
        LambdaQueryWrapper<PostFavoriteEntity> wrapper = new LambdaQueryWrapper<PostFavoriteEntity>()
                .eq(PostFavoriteEntity::getUserId, userId)
                .orderByDesc(PostFavoriteEntity::getCreatedAt)
                .last("LIMIT " + limit);
        if (cursor != null) {
            wrapper.lt(PostFavoriteEntity::getCreatedAt, cursor);
        }

        return mapper.selectList(wrapper).stream()
                .map(e -> PostFavorite.reconstitute(e.getId(), e.getPostId(), e.getUserId(), e.getCreatedAt()))
                .toList();
    }

    @Override
    public int countByPostId(Long postId) {
        return Math.toIntExact(mapper.selectCount(
                new LambdaQueryWrapper<PostFavoriteEntity>()
                        .eq(PostFavoriteEntity::getPostId, postId)
        ));
    }

    @Override
    public List<Long> findFavoritedPostIds(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }

        QueryWrapper<PostFavoriteEntity> wrapper = new QueryWrapper<PostFavoriteEntity>()
                .select("post_id")
                .eq("user_id", userId)
                .in("post_id", postIds);

        return mapper.selectList(wrapper).stream()
                .map(PostFavoriteEntity::getPostId)
                .toList();
    }
}
