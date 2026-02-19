package com.blog.user.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户拉黑实体
 *
 * @author Blog Team
 */
@Getter
public class UserBlock {

    /**
     * 拉黑者ID
     */
    private final Long blockerId;

    /**
     * 被拉黑者ID
     */
    private final Long blockedId;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    private UserBlock(Long blockerId, Long blockedId, LocalDateTime createdAt) {
        Assert.notNull(blockerId, "拉黑者ID不能为空");
        Assert.isTrue(blockerId > 0, "拉黑者ID必须为正数");
        Assert.notNull(blockedId, "被拉黑者ID不能为空");
        Assert.isTrue(blockedId > 0, "被拉黑者ID必须为正数");
        
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("不能拉黑自己");
        }
        
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /**
     * 创建新的拉黑关系
     */
    public static UserBlock create(Long blockerId, Long blockedId) {
        return new UserBlock(blockerId, blockedId, LocalDateTime.now());
    }

    /**
     * 从持久化恢复
     */
    public static UserBlock reconstitute(Long blockerId, Long blockedId, LocalDateTime createdAt) {
        return new UserBlock(blockerId, blockedId, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserBlock userBlock = (UserBlock) o;
        return Objects.equals(blockerId, userBlock.blockerId) && 
               Objects.equals(blockedId, userBlock.blockedId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockerId, blockedId);
    }
}
