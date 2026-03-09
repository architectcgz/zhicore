package com.zhicore.content.infrastructure.sentinel;

/**
 * 文章服务代表性 URL 路由常量。
 */
public final class ContentRoutes {

    private ContentRoutes() {}

    public static final String TAGS_PREFIX = "/api/v1/tags";
    public static final String TAGS_HOT = TAGS_PREFIX + "/hot";
}
