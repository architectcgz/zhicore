package com.zhicore.content.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.content.domain.model.PostLike;
import com.zhicore.content.domain.repository.PostLikeRepository;
import com.zhicore.content.infrastructure.repository.mapper.PostLikeMapper;
import com.zhicore.content.infrastructure.repository.po.PostLikePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文章点赞仓储实现
 *
 * @author ZhiCore Team
 */
@Repository
@RequiredArgsConstructor
public class PostLikeRepositoryImpl implements PostLikeRepository {

    private final PostLikeMapper postLikeMapper;

    @Override
    public void save(PostLike like) {
        PostLikePO po = toPO(like);
        postLikeMapper.insert(po);
    }

    @Override
    public void delete(Long postId, Long userId) {
        LambdaQueryWrapper<PostLikePO> wrapper = new LambdaQueryWrapper<PostLikePO>()
                .eq(PostLikePO::getPostId, postId)
                .eq(PostLikePO::getUserId, userId);
        postLikeMapper.delete(wrapper);
    }

    @Override
    public Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId) {
        LambdaQueryWrapper<PostLikePO> wrapper = new LambdaQueryWrapper<PostLikePO>()
                .eq(PostLikePO::getPostId, postId)
                .eq(PostLikePO::getUserId, userId);
        PostLikePO po = postLikeMapper.selectOne(wrapper);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public boolean exists(Long postId, Long userId) {
        LambdaQueryWrapper<PostLikePO> wrapper = new LambdaQueryWrapper<PostLikePO>()
                .eq(PostLikePO::getPostId, postId)
                .eq(PostLikePO::getUserId, userId);
        return postLikeMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<PostLike> findByUserIdCursor(Long userId, LocalDateTime cursor, int limit) {
        List<PostLikePO> pos = postLikeMapper.findByUserIdCursor(userId, cursor, limit);
        return pos.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public int countByPostId(Long postId) {
        LambdaQueryWrapper<PostLikePO> wrapper = new LambdaQueryWrapper<PostLikePO>()
                .eq(PostLikePO::getPostId, postId);
        return Math.toIntExact(postLikeMapper.selectCount(wrapper));
    }

    @Override
    public List<Long> findLikedPostIds(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyList();
        }
        return postLikeMapper.findLikedPostIds(userId, postIds);
    }

    // ==================== 转换方法 ====================

    private PostLikePO toPO(PostLike like) {
        PostLikePO po = new PostLikePO();
        // PostLikePO uses composite key (postId, userId), no separate id field
        po.setPostId(like.getPostId());
        po.setUserId(like.getUserId());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(like.getCreatedAt()));
        return po;
    }

    private PostLike toDomain(PostLikePO po) {
        // PostLike has an id field for the entity, generate or use a composite key representation
        Long id = generateCompositeId(po.getPostId(), po.getUserId());
        return PostLike.reconstitute(id, po.getPostId(), po.getUserId(), 
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
