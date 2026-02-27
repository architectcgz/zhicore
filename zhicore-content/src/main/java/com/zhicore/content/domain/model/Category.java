package com.zhicore.content.domain.model;

import com.zhicore.common.exception.DomainException;
import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 分类实体
 *
 * @author ZhiCore Team
 */
@Getter
public class Category {

    /** 合法 slug 格式：小写字母、数字、连字符 */
    private static final Pattern VALID_SLUG = Pattern.compile("^[a-z0-9-]+$");

    /**
     * 分类ID
     */
    private final String id;

    /**
     * 分类名称
     */
    private String name;

    /**
     * URL友好的标识
     */
    private String slug;

    /**
     * 描述
     */
    private String description;

    /**
     * 父分类ID（支持多级分类）
     */
    private String parentId;

    /**
     * 排序顺序
     */
    private int sortOrder;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    // ==================== 构造函数 ====================

    private Category(String id, String name, String slug) {
        Assert.hasText(id, "分类ID不能为空");
        Assert.hasText(name, "分类名称不能为空");
        Assert.hasText(slug, "分类标识不能为空");

        this.id = id;
        this.name = name;
        this.slug = slug;
        this.sortOrder = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    private Category(String id, String name, String slug, String description,
                     String parentId, int sortOrder, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建分类
     */
    public static Category create(String id, String name, String slug) {
        return new Category(id, name, slug);
    }

    /**
     * 创建子分类
     */
    public static Category createChild(String id, String name, String slug, String parentId) {
        Category category = new Category(id, name, slug);
        category.parentId = parentId;
        return category;
    }

    /**
     * 从持久化恢复
     */
    public static Category reconstitute(String id, String name, String slug, String description,
                                        String parentId, int sortOrder,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Category(id, name, slug, description, parentId, sortOrder, createdAt, updatedAt);
    }

    // ==================== 领域行为 ====================

    /**
     * 更新分类信息
     */
    public void update(String name, String slug, String description) {
        if (name != null) {
            validateName(name);
            this.name = name;
        }
        if (slug != null) {
            validateSlug(slug);
            this.slug = slug;
        }
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 设置父分类
     */
    public void setParent(String parentId) {
        if (parentId != null && parentId.equals(this.id)) {
            throw new DomainException("分类不能设置自己为父分类");
        }
        this.parentId = parentId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 设置排序顺序
     */
    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 检查是否为顶级分类
     */
    public boolean isTopLevel() {
        return this.parentId == null;
    }

    // ==================== 私有方法 ====================

    private void validateName(String name) {
        if (name.length() > 50) {
            throw new DomainException("分类名称不能超过50个字符");
        }
    }

    private void validateSlug(String slug) {
        if (slug.length() > 50) {
            throw new DomainException("分类标识不能超过50个字符");
        }
        if (!VALID_SLUG.matcher(slug).matches()) {
            throw new DomainException("分类标识只能包含小写字母、数字和连字符");
        }
    }
}
