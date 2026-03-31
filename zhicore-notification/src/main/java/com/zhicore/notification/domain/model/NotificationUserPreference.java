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
    private final boolean publishEnabled;

    private NotificationUserPreference(Long userId,
                                       boolean likeEnabled,
                                       boolean commentEnabled,
                                       boolean followEnabled,
                                       boolean replyEnabled,
                                       boolean systemEnabled,
                                       boolean publishEnabled) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.isTrue(userId > 0, "用户ID必须为正数");
        this.userId = userId;
        this.likeEnabled = likeEnabled;
        this.commentEnabled = commentEnabled;
        this.followEnabled = followEnabled;
        this.replyEnabled = replyEnabled;
        this.systemEnabled = systemEnabled;
        this.publishEnabled = publishEnabled;
    }

    public static NotificationUserPreference defaults(Long userId) {
        return of(userId, true, true, true, true, true, true);
    }

    public static NotificationUserPreference of(Long userId,
                                                boolean likeEnabled,
                                                boolean commentEnabled,
                                                boolean followEnabled,
                                                boolean replyEnabled,
                                                boolean systemEnabled,
                                                boolean publishEnabled) {
        return new NotificationUserPreference(
                userId, likeEnabled, commentEnabled, followEnabled, replyEnabled, systemEnabled, publishEnabled);
    }

    public boolean supports(NotificationType type) {
        return switch (type) {
            case POST_LIKED, LIKE -> likeEnabled;
            case POST_COMMENTED, COMMENT -> commentEnabled;
            case USER_FOLLOWED, FOLLOW -> followEnabled;
            case COMMENT_REPLIED, REPLY -> replyEnabled;
            case SYSTEM_ANNOUNCEMENT, SYSTEM -> systemEnabled;
            case POST_PUBLISHED_BY_FOLLOWING, POST_PUBLISHED_DIGEST, POST_PUBLISHED -> publishEnabled;
            case SECURITY_ALERT -> systemEnabled;
        };
    }
}
