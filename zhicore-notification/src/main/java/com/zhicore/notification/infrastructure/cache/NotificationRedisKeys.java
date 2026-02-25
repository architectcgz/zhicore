package com.zhicore.notification.infrastructure.cache;

/**
 * 通知服务 Redis Key 定义
 * 
 * 命名规范：{service}:{id}:{entity}:{field}
 * 示例：notification:123:unread, notification:456:detail
 *
 * @author ZhiCore Team
 */
public final class NotificationRedisKeys {

    private static final String PREFIX = "notification";

    private NotificationRedisKeys() {
        // 工具类，禁止实例化
    }

    /**
     * 用户未读通知数量
     * Key: notification:{userId}:unread
     */
    public static String unreadCount(String userId) {
        return PREFIX + ":" + userId + ":unread";
    }

    /**
     * 聚合通知列表缓存
     * Key: notification:{userId}:aggregated:{page}:{size}
     */
    public static String aggregatedList(String userId, int page, int size) {
        return PREFIX + ":" + userId + ":aggregated:" + page + ":" + size;
    }

    /**
     * 聚合通知列表缓存模式（用于批量删除）
     * Key: notification:{userId}:aggregated:*
     */
    public static String aggregatedListPattern(String userId) {
        return PREFIX + ":" + userId + ":aggregated:*";
    }

    /**
     * 通知详情缓存
     * Key: notification:{notificationId}:detail
     */
    public static String detail(Long notificationId) {
        return PREFIX + ":" + notificationId + ":detail";
    }
}
