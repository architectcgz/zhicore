package com.zhicore.notification.application.dto;

import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知推送 DTO
 * 用于 WebSocket 实时推送
 *
 * @author ZhiCore Team
 */
@Data
@Builder
public class NotificationPushDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private NotificationType type;
    private Long actorId;
    private String targetType;
    private Long targetId;
    private String content;
    private LocalDateTime createdAt;

    /**
     * 从领域模型转换
     */
    public static NotificationPushDTO from(Notification notification) {
        return NotificationPushDTO.builder()
                .id(notification.getId())
                .type(notification.getType())
                .actorId(notification.getActorId())
                .targetType(notification.getTargetType())
                .targetId(notification.getTargetId())
                .content(notification.getContent())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
