package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 文章收藏表实体（R3）
 */
@Data
@TableName("post_favorites")
public class PostFavoriteEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long postId;

    private Long userId;

    private OffsetDateTime createdAt;
}

