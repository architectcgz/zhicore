package com.zhicore.comment.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.OffsetDateTime;

/**
 * 评论点赞实体
 * 使用复合主键 (commentId, userId)
 *
 * @author ZhiCore Team
 */
@Getter
public class CommentLike {

    /**
     * 评论ID
     */
    private final Long commentId;

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 创建时间
     */
    private final OffsetDateTime createdAt;

    public CommentLike(Long commentId, Long userId) {
        Assert.notNull(commentId, "评论ID不能为空");
        Assert.isTrue(commentId > 0, "评论ID必须为正数");
        Assert.notNull(userId, "用户ID不能为空");
        Assert.isTrue(userId > 0, "用户ID必须为正数");

        this.commentId = commentId;
        this.userId = userId;
        this.createdAt = OffsetDateTime.now();
    }

    public CommentLike(Long commentId, Long userId, OffsetDateTime createdAt) {
        this.commentId = commentId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    /**
     * 从持久化恢复
     */
    public static CommentLike reconstitute(Long commentId, Long userId, OffsetDateTime createdAt) {
        return new CommentLike(commentId, userId, createdAt);
    }
}
