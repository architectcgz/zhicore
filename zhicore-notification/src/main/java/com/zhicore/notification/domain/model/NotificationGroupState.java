package com.zhicore.notification.domain.model;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 通知聚合组状态投影。
 */
public class NotificationGroupState {

    private Long recipientId;
    private String groupKey;
    private NotificationType notificationType;
    private Long latestNotificationId;
    private int totalCount;
    private int unreadCount;
    private String targetType;
    private String targetId;
    private String latestContent;
    private OffsetDateTime latestTime;
    private List<String> actorIds = Collections.emptyList();

    public static Builder builder() {
        return new Builder();
    }

    public static NotificationGroupState fromNotification(Notification notification) {
        Assert.notNull(notification, "通知不能为空");
        return builder()
                .recipientId(notification.getRecipientId())
                .groupKey(resolveGroupKey(notification))
                .notificationType(notification.getType())
                .latestNotificationId(notification.getId())
                .totalCount(1)
                .unreadCount(notification.isRead() ? 0 : 1)
                .targetType(notification.getTargetType())
                .targetId(notification.getTargetId() != null ? String.valueOf(notification.getTargetId()) : null)
                .latestContent(notification.getContent())
                .latestTime(notification.getCreatedAt())
                .build();
    }

    public static String resolveGroupKey(Notification notification) {
        Assert.notNull(notification, "通知不能为空");
        if (StringUtils.hasText(notification.getGroupKey())) {
            return notification.getGroupKey();
        }
        StringBuilder builder = new StringBuilder(notification.getType().name());
        if (StringUtils.hasText(notification.getTargetType()) && notification.getTargetId() != null) {
            builder.append(':')
                    .append(notification.getTargetType())
                    .append(':')
                    .append(notification.getTargetId());
        }
        return builder.toString();
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public Long getLatestNotificationId() {
        return latestNotificationId;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getLatestContent() {
        return latestContent;
    }

    public OffsetDateTime getLatestTime() {
        return latestTime;
    }

    public List<String> getActorIds() {
        return actorIds;
    }

    public static final class Builder {
        private final NotificationGroupState state = new NotificationGroupState();

        public Builder recipientId(Long recipientId) {
            state.recipientId = recipientId;
            return this;
        }

        public Builder groupKey(String groupKey) {
            state.groupKey = groupKey;
            return this;
        }

        public Builder notificationType(NotificationType notificationType) {
            state.notificationType = notificationType;
            return this;
        }

        public Builder latestNotificationId(Long latestNotificationId) {
            state.latestNotificationId = latestNotificationId;
            return this;
        }

        public Builder totalCount(int totalCount) {
            state.totalCount = totalCount;
            return this;
        }

        public Builder unreadCount(int unreadCount) {
            state.unreadCount = unreadCount;
            return this;
        }

        public Builder targetType(String targetType) {
            state.targetType = targetType;
            return this;
        }

        public Builder targetId(String targetId) {
            state.targetId = targetId;
            return this;
        }

        public Builder latestContent(String latestContent) {
            state.latestContent = latestContent;
            return this;
        }

        public Builder latestTime(OffsetDateTime latestTime) {
            state.latestTime = latestTime;
            return this;
        }

        public Builder actorIds(List<String> actorIds) {
            state.actorIds = actorIds == null ? Collections.emptyList() : List.copyOf(actorIds);
            return this;
        }

        public NotificationGroupState build() {
            state.actorIds = state.actorIds == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(state.actorIds));
            return state;
        }
    }
}
