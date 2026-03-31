package com.zhicore.notification.application.dto;

import com.zhicore.notification.domain.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 聚合通知DTO（数据库查询结果）
 *
 * @author ZhiCore Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedNotificationDTO {

    /**
     * 通知类型
     */
    private NotificationType type;

    /**
     * 目标类型
     */
    private String targetType;

    /**
     * 目标ID
     */
    private String targetId;

    /**
     * 该组通知总数
     */
    private int totalCount;

    /**
     * 该组未读数
     */
    private int unreadCount;

    /**
     * 最新通知时间
     */
    private OffsetDateTime latestTime;

    /**
     * 最新通知ID
     */
    private String latestNotificationId;

    /**
     * 最新通知内容
     */
    private String latestContent;

    /**
     * 所有触发者ID列表
     */
    private List<String> actorIds;
}
