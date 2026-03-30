package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 分类数据库实体
 * 
 * 对应 categories 表，包含分类的基本信息。
 * 
 * @author ZhiCore Team
 */
@Data
@TableName("categories")
public class CategoryEntity {

    /**
     * 分类ID（雪花ID）
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 分类展示名称
     */
    private String name;

    /**
     * URL友好标识（唯一）
     */
    private String slug;

    /**
     * 分类描述
     */
    private String description;

    /**
     * 父分类ID（支持多级分类）
     */
    private Long parentId;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;
}
