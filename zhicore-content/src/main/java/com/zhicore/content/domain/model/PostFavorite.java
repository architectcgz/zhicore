package com.zhicore.content.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.OffsetDateTime;

/**
 * 文章收藏实体
 *
 * @author ZhiCore Team
 */
@Getter
public class PostFavorite {

    /**
     * 收藏ID
     */
    private final Long id;

    /**
     * 文章ID
     */
    private final PostId postId;

    /**
     * 用户ID
     */
    private final UserId userId;

    /**
     * 创建时间
     */
    private final OffsetDateTime createdAt;

    public PostFavorite(Long id, PostId postId, UserId userId) {
        Assert.notNull(id, "收藏ID不能为空");
        Assert.isTrue(id > 0, "收藏ID必须为正数");
        Assert.notNull(postId, "文章ID不能为空");
        Assert.notNull(userId, "用户ID不能为空");

        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.createdAt = OffsetDateTime.now();
    }

    public PostFavorite(Long id, PostId postId, UserId userId, OffsetDateTime createdAt) {
        Assert.notNull(id, "收藏ID不能为空");
        Assert.isTrue(id > 0, "收藏ID必须为正数");
        Assert.notNull(postId, "文章ID不能为空");
        Assert.notNull(userId, "用户ID不能为空");
        Assert.notNull(createdAt, "创建时间不能为空");

        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    /**
     * 从持久化恢复
     */
    public static PostFavorite reconstitute(Long id, PostId postId, UserId userId, OffsetDateTime createdAt) {
        return new PostFavorite(id, postId, userId, createdAt);
    }
}
