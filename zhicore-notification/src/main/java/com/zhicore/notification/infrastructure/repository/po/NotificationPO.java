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
     * 触发者ID
     */
    private Long actorId;

    /**
     * 通知分类
     */
    private Integer category;

    /**
     * 字符串通知类型
     */
    private String notificationType;

    /**
     * 目标类型
     */
    private String targetType;

    /**
     * 目标ID
     */
    private Long targetId;

    /**
     * 源事件ID
     */
    private String sourceEventId;

    /**
     * 分组键
     */
    private String groupKey;

    /**
     * 扩展载荷
     */
    private String payloadJson;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 重要程度
     */
    private Integer importance;

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
