package com.zhicore.notification.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 通知持久化对象
 *
 * @author ZhiCore Team
 */
@Data
@TableName("notifications")
public class NotificationPO {

    /**
     * 通知ID
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 接收者ID
     */
    private Long recipientId;

    /**
     * 通知类型
     */
    private Integer type;

    /**
     * 平台化通知分类
     */
    private String category;

    /**
     * 平台化事件编码
     */
    private String eventCode;

    /**
     * 平台化扩展元数据（JSON）
     */
    private String metadata;

    /**
     * 触发者ID
     */
    private Long actorId;

    /**
     * 目标类型
     */
    private String targetType;

    /**
     * 目标ID
     */
    private Long targetId;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 是否已读
     */
    private Boolean isRead;

    /**
     * 已读时间
     */
    private OffsetDateTime readAt;

    /**
     * 创建时间
     */
    private OffsetDateTime createdAt;
}
