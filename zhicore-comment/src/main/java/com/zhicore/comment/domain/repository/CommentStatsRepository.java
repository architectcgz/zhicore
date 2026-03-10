package com.zhicore.comment.domain.repository;

/**
 * 评论统计仓储接口。
 *
 * 收敛 application 层对底层 Mapper 的直接依赖，
 * 由基础设施层负责具体的持久化实现。
 */
public interface CommentStatsRepository {

    void incrementLikeCount(Long commentId);

    void decrementLikeCount(Long commentId);

    void incrementReplyCount(Long commentId);

    void decrementReplyCount(Long commentId);
}
