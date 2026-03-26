package com.zhicore.notification.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

/**
 * 用户通知默认偏好
 */
@Getter
public class UserNotificationPreference {

    private final Long userId;
    private final NotificationType notificationType;
    private final NotificationChannel channel;
    private final boolean enabled;

    private UserNotificationPreference(Long userId,
                                       NotificationType notificationType,
                                       NotificationChannel channel,
                                       boolean enabled) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.isTrue(userId > 0, "用户ID必须为正数");
        Assert.notNull(notificationType, "通知类型不能为空");
        Assert.notNull(channel, "通知渠道不能为空");
        this.userId = userId;
        this.notificationType = notificationType;
        this.channel = channel;
        this.enabled = enabled;
    }

    public static UserNotificationPreference of(Long userId,
                                                NotificationType notificationType,
                                                NotificationChannel channel,
                                                boolean enabled) {
        return new UserNotificationPreference(userId, notificationType, channel, enabled);
    }
}
