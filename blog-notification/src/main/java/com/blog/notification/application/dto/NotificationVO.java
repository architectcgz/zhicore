package com.blog.notification.application.dto;

import com.blog.api.dto.user.UserSimpleDTO;
import com.blog.notification.domain.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通知VO
 *
 * @author Blog Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVO {

    /**
     * 通知ID
     */
    private Long id;

    /**
     * 通知类型
     */
    private NotificationType type;

    /**
     * 触发者信息
     */
    private UserSimpleDTO actor;

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
    private boolean isRead;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
