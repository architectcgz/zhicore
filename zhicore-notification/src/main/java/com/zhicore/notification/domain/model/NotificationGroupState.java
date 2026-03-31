package com.zhicore.notification.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 通知聚合组状态投影。
 *
 * <p>该模型用于加速聚合通知查询，不替代明细表作为事实来源。</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    private LocalDateTime latestTime;

    @Builder.Default
    private List<String> actorIds = Collections.emptyList();

    /**
     * 根据通知明细构造首次写入的组状态。
     */
    public static NotificationGroupState fromNotification(Notification notification) {
        Assert.notNull(notification, "通知不能为空");
        return NotificationGroupState.builder()
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

    /**
     * 统一解析互动通知分组键。
     */
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
}
