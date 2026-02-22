package com.zhicore.content.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 文章点赞持久化对象
 * 使用复合主键 (postId, userId)
 *
 * @author ZhiCore Team
 */
@Data
@TableName("post_likes")
public class PostLikePO implements Serializable {

    private Long postId;

    private Long userId;

    private OffsetDateTime createdAt;
}
