package com.zhicore.notification.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 用户免打扰配置
 */
@Getter
public class UserNotificationDnd {

    private final Long userId;
    private final boolean enabled;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final Set<NotificationCategory> categories;
    private final Set<NotificationChannel> channels;

    private UserNotificationDnd(Long userId,
                                boolean enabled,
                                LocalTime startTime,
                                LocalTime endTime,
                                Set<NotificationCategory> categories,
                                Set<NotificationChannel> channels) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.isTrue(userId > 0, "用户ID必须为正数");
        Assert.notNull(startTime, "免打扰开始时间不能为空");
        Assert.notNull(endTime, "免打扰结束时间不能为空");
        Assert.isTrue(!startTime.equals(endTime), "免打扰开始时间和结束时间不能相同");
        this.userId = userId;
        this.enabled = enabled;
        this.startTime = startTime;
        this.endTime = endTime;
        this.categories = categories == null || categories.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(categories));
        this.channels = channels == null || channels.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(channels));
    }

    public static UserNotificationDnd of(Long userId,
                                         boolean enabled,
                                         LocalTime startTime,
                                         LocalTime endTime,
                                         Set<NotificationCategory> categories,
                                         Set<NotificationChannel> channels) {
        return new UserNotificationDnd(userId, enabled, startTime, endTime, categories, channels);
    }

    public static UserNotificationDnd disabled(Long userId) {
        return new UserNotificationDnd(
                userId,
                false,
                LocalTime.of(22, 0),
                LocalTime.of(8, 0),
                Collections.emptySet(),
                Collections.emptySet()
        );
    }

    public boolean isActiveAt(LocalTime currentTime) {
        if (!enabled || currentTime == null) {
            return false;
        }
        if (startTime.isBefore(endTime)) {
            return !currentTime.isBefore(startTime) && currentTime.isBefore(endTime);
        }
        return !currentTime.isBefore(startTime) || currentTime.isBefore(endTime);
    }

    public boolean appliesTo(NotificationCategory category, NotificationChannel channel) {
        boolean categoryMatched = categories.isEmpty() || (category != null && categories.contains(category));
        boolean channelMatched = channels.isEmpty() || (channel != null && channels.contains(channel));
        return categoryMatched && channelMatched;
    }
}
