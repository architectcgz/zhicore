package com.zhicore.comment.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 评论点赞持久化对象
 * 使用复合主键 (commentId, userId)
 *
 * @author ZhiCore Team
 */
@Data
@TableName("comment_likes")
public class CommentLikePO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long commentId;

    private Long userId;

    private OffsetDateTime createdAt;
}
