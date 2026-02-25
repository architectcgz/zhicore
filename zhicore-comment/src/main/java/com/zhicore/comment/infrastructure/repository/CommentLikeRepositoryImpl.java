package com.zhicore.comment.infrastructure.repository;

import com.zhicore.comment.domain.model.CommentLike;
import com.zhicore.comment.domain.repository.CommentLikeRepository;
import com.zhicore.comment.infrastructure.repository.mapper.CommentLikeMapper;
import com.zhicore.comment.infrastructure.repository.po.CommentLikePO;
import com.zhicore.common.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 评论点赞仓储实现
 *
 * @author ZhiCore Team
 */
@Repository
@RequiredArgsConstructor
public class CommentLikeRepositoryImpl implements CommentLikeRepository {

    private final CommentLikeMapper likeMapper;

    @Override
    public void save(CommentLike like) {
        CommentLikePO po = toPO(like);
        likeMapper.insert(po);
    }

    @Override
    public void delete(Long commentId, Long userId) {
        likeMapper.deleteByCommentIdAndUserId(commentId, userId);
    }

    @Override
    public boolean exists(Long commentId, Long userId) {
        return likeMapper.exists(commentId, userId);
    }

    @Override
    public int countByCommentId(Long commentId) {
        return likeMapper.countByCommentId(commentId);
    }

    @Override
    public List<Long> findLikedCommentIds(Long userId, List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return likeMapper.findLikedCommentIds(userId, commentIds);
    }

    private CommentLikePO toPO(CommentLike like) {
        CommentLikePO po = new CommentLikePO();
        // CommentLike uses composite key (commentId, userId), no separate id field
        po.setCommentId(like.getCommentId());
        po.setUserId(like.getUserId());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(like.getCreatedAt()));
        return po;
    }
}
