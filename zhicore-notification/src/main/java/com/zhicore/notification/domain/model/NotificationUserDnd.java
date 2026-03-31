package com.zhicore.notification.domain.model;

import lombok.Getter;
import org.springframework.util.Assert;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 用户免打扰配置。
 */
@Getter
public class NotificationUserDnd {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final Long userId;
    private final boolean enabled;
    private final String startTime;
    private final String endTime;
    private final String timezone;

    private NotificationUserDnd(Long userId, boolean enabled, String startTime, String endTime, String timezone) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.isTrue(userId > 0, "用户ID必须为正数");
        Assert.hasText(timezone, "时区不能为空");
        if (enabled) {
            Assert.hasText(startTime, "启用免打扰时开始时间不能为空");
            Assert.hasText(endTime, "启用免打扰时结束时间不能为空");
            parseTime(startTime);
            parseTime(endTime);
        }
        this.userId = userId;
        this.enabled = enabled;
        this.startTime = startTime;
        this.endTime = endTime;
        this.timezone = timezone;
    }

    public static NotificationUserDnd defaults(Long userId, String timezone) {
        return of(userId, false, null, null, timezone);
    }

    public static NotificationUserDnd of(Long userId, boolean enabled, String startTime, String endTime, String timezone) {
        return new NotificationUserDnd(userId, enabled, startTime, endTime, timezone);
    }

    public boolean isActive(LocalTime now) {
        if (!enabled || startTime == null || endTime == null || now == null) {
            return false;
        }
        LocalTime start = parseTime(startTime);
        LocalTime end = parseTime(endTime);
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("免打扰时间格式错误，应为 HH:mm");
        }
    }
}
