package com.zhicore.notification.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.notification.domain.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 通知VO
 *
 * @author ZhiCore Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVO {

    /**
     * 通知ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
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
    @JsonSerialize(using = ToStringSerializer.class)
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
    private OffsetDateTime createdAt;
}
