package com.blog.post.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.util.DateTimeUtils;
import com.blog.post.domain.model.PostFavorite;
import com.blog.post.domain.repository.PostFavoriteRepository;
import com.blog.post.infrastructure.repository.mapper.PostFavoriteMapper;
import com.blog.post.infrastructure.repository.po.PostFavoritePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文章收藏仓储实现
 *
 * @author Blog Team
 */
@Repository
@RequiredArgsConstructor
public class PostFavoriteRepositoryImpl implements PostFavoriteRepository {

    private final PostFavoriteMapper postFavoriteMapper;

    @Override
    public void save(PostFavorite favorite) {
        PostFavoritePO po = toPO(favorite);
        postFavoriteMapper.insert(po);
    }

    @Override
    public void delete(Long postId, Long userId) {
        LambdaQueryWrapper<PostFavoritePO> wrapper = new LambdaQueryWrapper<PostFavoritePO>()
                .eq(PostFavoritePO::getPostId, postId)
                .eq(PostFavoritePO::getUserId, userId);
        postFavoriteMapper.delete(wrapper);
    }

    @Override
    public Optional<PostFavorite> findByPostIdAndUserId(Long postId, Long userId) {
        LambdaQueryWrapper<PostFavoritePO> wrapper = new LambdaQueryWrapper<PostFavoritePO>()
                .eq(PostFavoritePO::getPostId, postId)
                .eq(PostFavoritePO::getUserId, userId);
        PostFavoritePO po = postFavoriteMapper.selectOne(wrapper);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public boolean exists(Long postId, Long userId) {
        LambdaQueryWrapper<PostFavoritePO> wrapper = new LambdaQueryWrapper<PostFavoritePO>()
                .eq(PostFavoritePO::getPostId, postId)
                .eq(PostFavoritePO::getUserId, userId);
        return postFavoriteMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<PostFavorite> findByUserIdCursor(Long userId, LocalDateTime cursor, int limit) {
        List<PostFavoritePO> pos = postFavoriteMapper.findByUserIdCursor(userId, cursor, limit);
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public int countByPostId(Long postId) {
        LambdaQueryWrapper<PostFavoritePO> wrapper = new LambdaQueryWrapper<PostFavoritePO>()
                .eq(PostFavoritePO::getPostId, postId);
        return Math.toIntExact(postFavoriteMapper.selectCount(wrapper));
    }

    @Override
    public List<Long> findFavoritedPostIds(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyList();
        }
        return postFavoriteMapper.findFavoritedPostIds(userId, postIds);
    }

    // ==================== 转换方法 ====================

    private PostFavoritePO toPO(PostFavorite favorite) {
        PostFavoritePO po = new PostFavoritePO();
        // PostFavoritePO uses composite key (postId, userId), no separate id field
        po.setPostId(favorite.getPostId());
        po.setUserId(favorite.getUserId());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(favorite.getCreatedAt()));
        return po;
    }

    private PostFavorite toDomain(PostFavoritePO po) {
        // PostFavorite has an id field for the entity, generate or use a composite key representation
        Long id = generateCompositeId(po.getPostId(), po.getUserId());
        return PostFavorite.reconstitute(id, po.getPostId(), po.getUserId(), 
                DateTimeUtils.toLocalDateTime(po.getCreatedAt()));
    }
    
    /**
     * Generate a composite ID from postId and userId
     * This is a simple hash-based approach for the entity ID
     */
    private Long generateCompositeId(Long postId, Long userId) {
        // Simple combination: not perfect but works for reconstitution
        return (postId << 32) | (userId & 0xFFFFFFFFL);
    }
}
