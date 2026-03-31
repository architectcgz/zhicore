package com.zhicore.content.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

/**
 * 标签聚合根（充血模型）
 * 
 * 设计原则：
 * 1. Tag 是独立的聚合根，具有全局唯一的 slug
 * 2. slug 一旦创建不可变，作为自然键使用
 * 3. name 可以修改，但 slug 保持不变
 * 4. 私有构造函数 + 工厂方法
 *
 * @author ZhiCore Team
 */
@Getter
public class Tag {

    /**
     * 标签ID（雪花ID）
     */
    private final Long id;

    /**
     * 展示名称（如 "PostgreSQL"）
     */
    private String name;

    /**
     * URL友好标识（如 "postgresql"）
     * 全局唯一，一旦创建不可变
     */
    private final String slug;

    /**
     * 标签描述（可选）
     */
    private String description;

    /**
     * 创建时间
     */
    private final OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;

    // ==================== 构造函数 ====================

    /**
     * 私有构造函数（创建新标签）
     * 
     * @param id 标签ID
     * @param name 标签名称
     * @param slug 标签slug
     */
    private Tag(Long id, String name, String slug) {
        Assert.notNull(id, "标签ID不能为空");
        Assert.isTrue(id > 0, "标签ID必须为正数");
        Assert.hasText(name, "标签名称不能为空");
        Assert.hasText(slug, "标签slug不能为空");

        this.id = id;
        this.name = name.trim();
        this.slug = slug;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 私有构造函数（从持久化恢复）
     * 使用 @JsonCreator 支持 Jackson 反序列化
     */
    @JsonCreator
    private Tag(@JsonProperty("id") Long id,
                @JsonProperty("name") String name,
                @JsonProperty("slug") String slug,
                @JsonProperty("description") String description,
                @JsonProperty("createdAt") OffsetDateTime createdAt,
                @JsonProperty("updatedAt") OffsetDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建新标签
     * 
     * @param id 标签ID（由ID生成器生成）
     * @param name 标签名称
     * @param slug 标签slug（由TagDomainService规范化生成）
     * @return 标签实例
     */
    public static Tag create(Long id, String name, String slug) {
        return new Tag(id, name, slug);
    }

    /**
     * 从持久化恢复标签
     * 
     * @param id 标签ID
     * @param name 标签名称
     * @param slug 标签slug
     * @param description 标签描述
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @return 标签实例
     */
    public static Tag reconstitute(Long id, String name, String slug,
                                   String description, OffsetDateTime createdAt,
                                   OffsetDateTime updatedAt) {
        return new Tag(id, name, slug, description, createdAt, updatedAt);
    }

    // ==================== 领域行为 ====================

    /**
     * 更新标签名称
     * 注意：slug 不可变，只能更新 name
     * 
     * @param name 新的标签名称
     */
    public void updateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("标签名称不能为空");
        }
        if (name.length() > 50) {
            throw new IllegalArgumentException("标签名称不能超过50字符");
        }
        this.name = name.trim();
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 更新标签描述
     * 
     * @param description 新的标签描述
     */
    public void updateDescription(String description) {
        if (description != null && description.length() > 200) {
            throw new IllegalArgumentException("标签描述不能超过200字符");
        }
        this.description = description;
        this.updatedAt = OffsetDateTime.now();
    }

    // ==================== 查询方法 ====================

    /**
     * 检查是否有描述
     * 
     * @return 是否有描述
     */
    public boolean hasDescription() {
        return StringUtils.hasText(this.description);
    }

    // ==================== equals & hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return id.equals(tag.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Tag{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", slug='" + slug + '\'' +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
