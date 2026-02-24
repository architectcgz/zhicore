package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签数据库实体
 * 
 * 对应 tags 表，包含标签的基本信息。
 * 
 * @author ZhiCore Team
 */
@Data
@TableName("tags")
public class TagEntity {

    /**
     * 标签ID（雪花ID）
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 标签展示名称
     */
    private String name;

    /**
     * URL友好标识（唯一）
     */
    private String slug;

    /**
     * 标签描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
