package com.zhicore.content.infrastructure.persistence.pg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章数据库实体
 * 
 * 对应 posts 表，包含 write_state 和 incomplete_reason 字段用于三阶段写入跟踪。
 * 
 * @author ZhiCore Team
 */
@Data
@TableName("posts")
public class PostEntity {

    /**
     * 文章ID（雪花ID）
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 作者ID
     */
    private Long ownerId;

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

    /**
     * 标题
     */
    private String title;

    /**
     * 摘要
     */
    private String excerpt;

    /**
     * 封面图文件ID（UUIDv7格式）
     */
    private String coverImageId;

    /**
     * 状态：0-草稿 1-已发布 2-定时发布 3-已删除
     */
    private Integer status;

    /**
     * 写入状态（用于三阶段写入流程跟踪）
     * 
     * 可选值：
     * - NONE: 未开始
     * - DRAFT_ONLY: 只有 PG 草稿
     * - CONTENT_SAVED: PG + MongoDB 都有
     * - PUBLISHED: 已发布
     * - INCOMPLETE: 不完整，需要清理
     */
    private String writeState;

    /**
     * 不完整原因（当 writeState 为 INCOMPLETE 时记录）
     */
    private String incompleteReason;

    /**
     * 话题ID
     */
    private Long topicId;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 定时发布时间
     */
    private LocalDateTime scheduledAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 是否已归档
     */
    private Boolean isArchived;

    /**
     * 乐观锁版本号
     */
    @Version
    private Long version;
}
