package com.blog.user.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

/**
 * 用户关注统计值对象
 *
 * @author Blog Team
 */
@Getter
public class UserFollowStats {

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 粉丝数
     */
    private int followersCount;

    /**
     * 关注数
     */
    private int followingCount;

    private UserFollowStats(Long userId, int followersCount, int followingCount) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.isTrue(userId > 0, "用户ID必须为正数");
        this.userId = userId;
        this.followersCount = Math.max(0, followersCount);
        this.followingCount = Math.max(0, followingCount);
    }

    /**
     * 创建新的统计
     */
    public static UserFollowStats create(Long userId) {
        return new UserFollowStats(userId, 0, 0);
    }

    /**
     * 从持久化恢复
     */
    public static UserFollowStats reconstitute(Long userId, int followersCount, int followingCount) {
        return new UserFollowStats(userId, followersCount, followingCount);
    }

    /**
     * 增加粉丝数
     */
    public void incrementFollowers() {
        this.followersCount++;
    }

    /**
     * 减少粉丝数
     */
    public void decrementFollowers() {
        this.followersCount = Math.max(0, this.followersCount - 1);
    }

    /**
     * 增加关注数
     */
    public void incrementFollowing() {
        this.followingCount++;
    }

    /**
     * 减少关注数
     */
    public void decrementFollowing() {
        this.followingCount = Math.max(0, this.followingCount - 1);
    }
}
