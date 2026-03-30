package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 文章点赞表实体（R2）
 */
@Data
@TableName("post_likes")
public class PostLikeEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long postId;

    private Long userId;

    private OffsetDateTime createdAt;
}

