package com.zhicore.notification.infrastructure.sentinel;

/**
 * 通知服务路由常量。
 */
public final class NotificationRoutes {

    private NotificationRoutes() {
    }

    public static final String PREFIX = "/api/v1/notifications";
    public static final String UNREAD_COUNT = PREFIX + "/unread/count";
}
