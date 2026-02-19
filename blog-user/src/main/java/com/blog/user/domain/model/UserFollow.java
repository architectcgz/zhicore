package com.blog.user.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户关注实体
 *
 * @author Blog Team
 */
@Getter
public class UserFollow {

    /**
     * 关注者ID
     */
    private final Long followerId;

    /**
     * 被关注者ID
     */
    private final Long followingId;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    /**
     * 私有构造函数
     */
    private UserFollow(Long followerId, Long followingId, LocalDateTime createdAt) {
        Assert.notNull(followerId, "关注者ID不能为空");
        Assert.isTrue(followerId > 0, "关注者ID必须为正数");
        Assert.notNull(followingId, "被关注者ID不能为空");
        Assert.isTrue(followingId > 0, "被关注者ID必须为正数");
        
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("不能关注自己");
        }
        
        this.followerId = followerId;
        this.followingId = followingId;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /**
     * 创建新的关注关系
     *
     * @param followerId 关注者ID
     * @param followingId 被关注者ID
     * @return 关注实体
     */
    public static UserFollow create(Long followerId, Long followingId) {
        return new UserFollow(followerId, followingId, LocalDateTime.now());
    }

    /**
     * 从持久化恢复
     *
     * @param followerId 关注者ID
     * @param followingId 被关注者ID
     * @param createdAt 创建时间
     * @return 关注实体
     */
    public static UserFollow reconstitute(Long followerId, Long followingId, LocalDateTime createdAt) {
        return new UserFollow(followerId, followingId, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserFollow that = (UserFollow) o;
        return Objects.equals(followerId, that.followerId) && 
               Objects.equals(followingId, that.followingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(followerId, followingId);
    }
}
