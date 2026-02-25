package com.zhicore.comment.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 评论持久化对象
 *
 * @author ZhiCore Team
 */
@Data
@TableName("comments")
public class CommentPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long postId;

    private Long authorId;

    private Long parentId;

    private Long rootId;

    private Long replyToUserId;

    private String content;

    @TableField("image_ids")
    private String[] imageIds;

    @TableField("voice_id")
    private String voiceId;

    @TableField("voice_duration")
    private Integer voiceDuration;

    private Integer status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /**
     * 点赞数（从 comment_stats 表关联查询）
     */
    @TableField(exist = false)
    private Integer likeCount;

    /**
     * 回复数（从 comment_stats 表关联查询）
     */
    @TableField(exist = false)
    private Integer replyCount;
}
