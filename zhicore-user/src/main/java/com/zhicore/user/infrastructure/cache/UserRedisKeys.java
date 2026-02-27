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

    /**
     * 用户简要信息缓存
     * Key: user:{userId}:simple
     */
    public static String userSimple(Long userId) {
        return PREFIX + ":" + userId + ":simple";
    }

    /**
     * 用户所有业务缓存键（用于写操作后统一失效）
     * 包含：detail、simple
     */
    public static String[] allCacheKeys(Long userId) {
        return new String[] { userDetail(userId), userSimple(userId) };
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
     * Refresh Token 白名单键
     * Key: user:{userId}:token:refresh:{tokenId}
     * 每个 Refresh Token 独立存储，支持多设备登录和单独吊销
     */
    public static String refreshTokenWhitelist(Long userId, String tokenId) {
        return PREFIX + ":" + userId + ":token:refresh:" + tokenId;
    }

    /**
     * Refresh Token 白名单模式匹配键
     * Key: user:{userId}:token:refresh:*
     * 用于清除用户所有 Refresh Token（禁用/修改密码/强制下线）
     */
    public static String refreshTokenPattern(Long userId) {
        return PREFIX + ":" + userId + ":token:refresh:*";
    }

    /**
     * 用户所有令牌的模式匹配键
     * Key: user:{userId}:token:*
     */
    public static String userTokenPattern(Long userId) {
        return PREFIX + ":" + userId + ":token:*";
    }

    // ==================== 拉黑操作锁 ====================

    /**
     * 拉黑操作锁
     * Key: lock:block:{minId}:{maxId}
     * 按 userId 大小排序，防止死锁
     */
    public static String blockLock(Long userIdA, Long userIdB) {
        long minId = Math.min(userIdA, userIdB);
        long maxId = Math.max(userIdA, userIdB);
        return "lock:block:" + minId + ":" + maxId;
    }
}
