package com.zhicore.comment.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 评论统计持久化对象
 *
 * @author ZhiCore Team
 */
@Data
@TableName("comment_stats")
public class CommentStatsPO {

    @TableId(type = IdType.INPUT)
    private Long commentId;

    private Integer likeCount;

    private Integer replyCount;
}
