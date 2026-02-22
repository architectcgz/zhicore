package com.zhicore.content.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

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
    private final Long postId;

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    public PostFavorite(Long id, Long postId, Long userId) {
        Assert.notNull(id, "收藏ID不能为空");
        Assert.isTrue(id > 0, "收藏ID必须为正数");
        Assert.notNull(postId, "文章ID不能为空");
        Assert.isTrue(postId > 0, "文章ID必须为正数");
        Assert.notNull(userId, "用户ID不能为空");
        Assert.isTrue(userId > 0, "用户ID必须为正数");

        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }

    public PostFavorite(Long id, Long postId, Long userId, LocalDateTime createdAt) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    /**
     * 从持久化恢复
     */
    public static PostFavorite reconstitute(Long id, Long postId, Long userId, LocalDateTime createdAt) {
        return new PostFavorite(id, postId, userId, createdAt);
    }
}
