package com.zhicore.notification.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

/**
 * 用户通知偏好。
 */
@Getter
public class NotificationUserPreference {

    private final Long userId;
    private final boolean likeEnabled;
    private final boolean commentEnabled;
    private final boolean followEnabled;
    private final boolean replyEnabled;
    private final boolean systemEnabled;

    private NotificationUserPreference(Long userId,
                                       boolean likeEnabled,
                                       boolean commentEnabled,
                                       boolean followEnabled,
                                       boolean replyEnabled,
                                       boolean systemEnabled) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.isTrue(userId > 0, "用户ID必须为正数");
        this.userId = userId;
        this.likeEnabled = likeEnabled;
        this.commentEnabled = commentEnabled;
        this.followEnabled = followEnabled;
        this.replyEnabled = replyEnabled;
        this.systemEnabled = systemEnabled;
    }

    public static NotificationUserPreference defaults(Long userId) {
        return of(userId, true, true, true, true, true);
    }

    public static NotificationUserPreference of(Long userId,
                                                boolean likeEnabled,
                                                boolean commentEnabled,
                                                boolean followEnabled,
                                                boolean replyEnabled,
                                                boolean systemEnabled) {
        return new NotificationUserPreference(
                userId, likeEnabled, commentEnabled, followEnabled, replyEnabled, systemEnabled);
    }

    public boolean supports(NotificationType type) {
        return switch (type) {
            case LIKE -> likeEnabled;
            case COMMENT -> commentEnabled;
            case FOLLOW -> followEnabled;
            case REPLY -> replyEnabled;
            case SYSTEM -> systemEnabled;
        };
    }
}
