package com.zhicore.common.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * 日期时间转换工具类。
 */
public final class DateTimeUtils {

    public static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    /**
     * 业务时区：中国标准时间 (UTC+8)。
     * 所有日期敏感业务（签到、日榜等）必须使用此时区。
     */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private DateTimeUtils() {
    }

    public static OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(timestamp.toInstant(), SYSTEM_ZONE);
    }

    public static OffsetDateTime toOffsetDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(date.toInstant(), SYSTEM_ZONE);
    }

    public static Instant toInstant(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.toInstant();
    }

    public static Timestamp toTimestamp(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return Timestamp.from(offsetDateTime.toInstant());
    }

    public static OffsetDateTime nowOffset() {
        return OffsetDateTime.now(SYSTEM_ZONE);
    }

    public static LocalDate businessDate() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    public static OffsetDateTime businessOffsetDateTime() {
        return OffsetDateTime.now(BUSINESS_ZONE);
    }

    public static LocalDate toBusinessDate(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.atZoneSameInstant(BUSINESS_ZONE).toLocalDate();
    }

    public static OffsetDateTime toBusinessOffsetDateTime(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.atZoneSameInstant(BUSINESS_ZONE).toOffsetDateTime();
    }
}
