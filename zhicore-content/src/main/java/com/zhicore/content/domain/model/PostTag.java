package com.zhicore.content.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.util.Assert;

import java.time.OffsetDateTime;

/**
 * Post-Tag 关联值对象
 * 
 * 设计原则：
 * 1. PostTag 不是聚合根，而是关联关系的表示
 * 2. 通过复合主键 (postId, tagId) 保证唯一性
 * 3. 创建时间用于追踪关联历史
 * 4. 不可变对象（所有字段都是 final）
 *
 * @author ZhiCore Team
 */
@Getter
public class PostTag {

    /**
     * 文章ID
     */
    private final PostId postId;

    /**
     * 标签ID
     */
    private final TagId tagId;

    /**
     * 关联创建时间
     */
    private final OffsetDateTime createdAt;

    // ==================== 构造函数 ====================

    /**
     * 私有构造函数（创建新关联）
     * 
     * @param postId 文章ID
     * @param tagId 标签ID
     */
    private PostTag(PostId postId, TagId tagId) {
        Assert.notNull(postId, "文章ID不能为空");
        Assert.notNull(tagId, "标签ID不能为空");

        this.postId = postId;
        this.tagId = tagId;
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * 私有构造函数（从持久化恢复）
     * 使用 @JsonCreator 支持 Jackson 反序列化
     */
    @JsonCreator
    private PostTag(@JsonProperty("postId") PostId postId,
                    @JsonProperty("tagId") TagId tagId,
                    @JsonProperty("createdAt") OffsetDateTime createdAt) {
        Assert.notNull(postId, "文章ID不能为空");
        Assert.notNull(tagId, "标签ID不能为空");
        Assert.notNull(createdAt, "创建时间不能为空");

        this.postId = postId;
        this.tagId = tagId;
        this.createdAt = createdAt;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建新的 Post-Tag 关联
     * 
     * @param postId 文章ID
     * @param tagId 标签ID
     * @return PostTag 实例
     */
    public static PostTag create(PostId postId, TagId tagId) {
        return new PostTag(postId, tagId);
    }

    /**
     * 从持久化恢复 Post-Tag 关联
     * 
     * @param postId 文章ID
     * @param tagId 标签ID
     * @param createdAt 创建时间
     * @return PostTag 实例
     */
    public static PostTag reconstitute(PostId postId, TagId tagId, OffsetDateTime createdAt) {
        return new PostTag(postId, tagId, createdAt);
    }

    // ==================== equals & hashCode ====================

    /**
     * 基于 postId 和 tagId 的复合主键判断相等性
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostTag postTag = (PostTag) o;
        return postId.equals(postTag.postId) && tagId.equals(postTag.tagId);
    }

    @Override
    public int hashCode() {
        int result = postId.hashCode();
        result = 31 * result + tagId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PostTag{" +
                "postId=" + postId +
                ", tagId=" + tagId +
                ", createdAt=" + createdAt +
                '}';
    }
}
