package com.zhicore.comment.infrastructure.repository;

import com.zhicore.comment.domain.repository.CommentStatsRepository;
import com.zhicore.comment.infrastructure.repository.mapper.CommentStatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 评论统计仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class CommentStatsRepositoryImpl implements CommentStatsRepository {

    private final CommentStatsMapper commentStatsMapper;

    @Override
    public void incrementLikeCount(Long commentId) {
        commentStatsMapper.incrementLikeCount(commentId);
    }

    @Override
    public void decrementLikeCount(Long commentId) {
        commentStatsMapper.decrementLikeCount(commentId);
    }

    @Override
    public void incrementReplyCount(Long commentId) {
        commentStatsMapper.incrementReplyCount(commentId);
    }

    @Override
    public void decrementReplyCount(Long commentId) {
        commentStatsMapper.decrementReplyCount(commentId);
    }
}
