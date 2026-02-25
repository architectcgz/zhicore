package com.zhicore.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 日期时间转换工具类
 * 用于在领域层 (LocalDateTime) 和基础设施层 (OffsetDateTime) 之间进行转换
 *
 * @author ZhiCore Team
 */
public final class DateTimeUtils {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    
    /**
     * 业务时区：中国标准时间 (UTC+8)
     * 所有日期敏感业务（签到、日榜等）必须使用此时区
     */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private DateTimeUtils() {
        // 工具类不允许实例化
    }

    /**
     * 将 OffsetDateTime 转换为 LocalDateTime
     * 用于从数据库读取数据后转换为领域模型
     *
     * @param offsetDateTime 带时区的时间
     * @return 本地时间，如果输入为 null 则返回 null
     */
    public static LocalDateTime toLocalDateTime(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.toLocalDateTime();
    }

    /**
     * 将 LocalDateTime 转换为 OffsetDateTime
     * 用于将领域模型保存到数据库
     *
     * @param localDateTime 本地时间
     * @return 带时区的时间，如果输入为 null 则返回 null
     */
    public static OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        ZoneOffset offset = SYSTEM_ZONE.getRules().getOffset(localDateTime);
        return localDateTime.atOffset(offset);
    }

    /**
     * 获取当前时间的 OffsetDateTime
     *
     * @return 当前时间（带时区）
     */
    public static OffsetDateTime nowOffset() {
        return OffsetDateTime.now(SYSTEM_ZONE);
    }

    /**
     * 获取当前时间的 LocalDateTime
     *
     * @return 当前时间（本地）
     */
    public static LocalDateTime nowLocal() {
        return LocalDateTime.now(SYSTEM_ZONE);
    }

    /**
     * 获取业务日期（基于业务时区）
     * 用于签到、日榜等日期敏感业务
     *
     * @return 业务时区的当前日期
     */
    public static LocalDate businessDate() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    /**
     * 获取业务时间（基于业务时区）
     * 用于需要精确到时间的业务场景
     *
     * @return 业务时区的当前时间
     */
    public static LocalDateTime businessDateTime() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    /**
     * 获取业务时区的 OffsetDateTime
     *
     * @return 业务时区的当前时间（带时区）
     */
    public static OffsetDateTime businessOffsetDateTime() {
        return OffsetDateTime.now(BUSINESS_ZONE);
    }

    /**
     * 将 UTC 时间转换为业务时区日期
     * 用于测试跨时区场景
     *
     * @param utcDateTime UTC 时间
     * @return 业务时区的日期
     */
    public static LocalDate toBusinessDate(OffsetDateTime utcDateTime) {
        if (utcDateTime == null) {
            return null;
        }
        return utcDateTime.atZoneSameInstant(BUSINESS_ZONE).toLocalDate();
    }

    /**
     * 将 UTC 时间转换为业务时区时间
     * 用于测试跨时区场景
     *
     * @param utcDateTime UTC 时间
     * @return 业务时区的时间
     */
    public static LocalDateTime toBusinessDateTime(OffsetDateTime utcDateTime) {
        if (utcDateTime == null) {
            return null;
        }
        return utcDateTime.atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime();
    }
}
