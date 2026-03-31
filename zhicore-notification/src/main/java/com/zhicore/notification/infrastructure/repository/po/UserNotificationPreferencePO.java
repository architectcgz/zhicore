package com.zhicore.notification.infrastructure.repository.po;

import lombok.Data;

/**
 * 用户通知偏好持久化对象
 */
@Data
public class UserNotificationPreferencePO {

    private Long userId;
    private String notificationType;
    private String channel;
    private Boolean enabled;
}
