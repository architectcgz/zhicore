package com.zhicore.message.infrastructure.sentinel;

/**
 * 消息服务代表性 URL 路由常量。
 */
public final class MessageRoutes {

    private MessageRoutes() {}

    public static final String PREFIX = "/api/v1/messages";
    public static final String UNREAD_COUNT = PREFIX + "/unread/count";
}
