package com.blog.notification.application.dto;

import com.blog.api.dto.user.UserSimpleDTO;
import com.blog.notification.domain.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聚合通知VO（返回给前端）
 *
 * @author Blog Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedNotificationVO {

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
    private Long targetId;

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
    private LocalDateTime latestTime;

    /**
     * 最新通知内容
     */
    private String latestContent;

    /**
     * 最近的几个触发者
     */
    private List<UserSimpleDTO> recentActors;

    /**
     * 聚合后的文案（如"张三等5人赞了你的文章"）
     */
    private String aggregatedContent;
}
