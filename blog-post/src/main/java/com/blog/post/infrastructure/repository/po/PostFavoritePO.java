package com.blog.post.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 文章收藏持久化对象
 * 使用复合主键 (postId, userId)
 *
 * @author Blog Team
 */
@Data
@TableName("post_favorites")
public class PostFavoritePO implements Serializable {

    private Long postId;

    private Long userId;

    private OffsetDateTime createdAt;
}
