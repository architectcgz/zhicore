package com.zhicore.user.infrastructure.cache;

import java.time.YearMonth;

/**
 * 用户服务 Redis Key 定义
 * 
 * 命名规范：{service}:{id}:{entity}:{field}
 * 示例：user:123:detail, user:123:stats:following
 *
 * @author ZhiCore Team
 */
public final class UserRedisKeys {

    private static final String PREFIX = "user";

    private UserRedisKeys() {
        // 工具类，禁止实例化
    }

    // ==================== 用户缓存 ====================

    /**
     * 用户详情缓存
     * Key: user:{userId}:detail
     */
    public static String userDetail(Long userId) {
        return PREFIX + ":" + userId + ":detail";
    }

    // ==================== 关注统计 ====================

    /**
     * 用户关注数
     * Key: user:{userId}:stats:following
     */
    public static String followingCount(Long userId) {
        return PREFIX + ":" + userId + ":stats:following";
    }

    /**
     * 用户粉丝数
     * Key: user:{userId}:stats:followers
     */
    public static String followersCount(Long userId) {
        return PREFIX + ":" + userId + ":stats:followers";
    }

    // ==================== 签到 ====================

    /**
     * 用户签到位图
     * Key: user:{userId}:checkin:{yearMonth}
     */
    public static String checkInBitmap(Long userId, YearMonth yearMonth) {
        return PREFIX + ":" + userId + ":checkin:" + yearMonth.toString();
    }

    // ==================== 分布式锁 ====================

    /**
     * 用户详情锁键
     * Key: user:lock:detail:{userId}
     */
    public static String lockDetail(Long userId) {
        return PREFIX + ":lock:detail:" + userId;
    }

    /**
     * 关注操作锁
     * Key: user:{followerId}:lock:follow:{followingId}
     */
    public static String followLock(Long followerId, Long followingId) {
        return PREFIX + ":" + followerId + ":lock:follow:" + followingId;
    }

    /**
     * 签到操作锁
     * Key: user:{userId}:lock:checkin:{date}
     */
    public static String checkInLock(Long userId, String date) {
        return PREFIX + ":" + userId + ":lock:checkin:" + date;
    }

    // ==================== Token 缓存 ====================

    /**
     * 用户访问令牌缓存键
     * Key: user:{userId}:token:access
     */
    public static String accessToken(Long userId) {
        return PREFIX + ":" + userId + ":token:access";
    }

    /**
     * 用户刷新令牌缓存键
     * Key: user:{userId}:token:refresh
     */
    public static String refreshToken(Long userId) {
        return PREFIX + ":" + userId + ":token:refresh";
    }

    /**
     * 用户所有令牌的模式匹配键
     * Key: user:{userId}:token:*
     */
    public static String userTokenPattern(Long userId) {
        return PREFIX + ":" + userId + ":token:*";
    }
}
