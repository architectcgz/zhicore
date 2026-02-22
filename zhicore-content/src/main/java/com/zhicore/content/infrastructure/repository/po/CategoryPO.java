package com.zhicore.content.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类持久化对象
 *
 * @author ZhiCore Team
 */
@Data
@TableName("categories")
public class CategoryPO {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private String slug;

    private String description;

    private String parentId;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
