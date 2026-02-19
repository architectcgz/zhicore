package com.blog.post.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 文章持久化对象
 *
 * @author Blog Team
 */
@Data
@TableName("posts")
public class PostPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long ownerId;

    private String title;

    private String raw;

    private String html;

    private String excerpt;

    /**
     * 封面图文件ID（UUIDv7格式）
     */
    private String coverImageId;

    /**
     * 状态：0-草稿 1-已发布 2-定时发布 3-已删除
     */
    private Integer status;

    private Long topicId;

    private OffsetDateTime publishedAt;

    private OffsetDateTime scheduledAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /**
     * 作者昵称快照（冗余字段）
     */
    private String ownerName;

    /**
     * 作者头像文件ID快照（冗余字段，UUIDv7格式）
     */
    private String ownerAvatarId;

    /**
     * 作者资料版本号（用于防止消息乱序）
     */
    private Long ownerProfileVersion;
}
