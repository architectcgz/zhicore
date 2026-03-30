package com.zhicore.notification.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

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

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private NotificationType type;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorId;
    private String targetType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;
    private String content;
    private OffsetDateTime createdAt;

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
