package com.zhicore.search.infrastructure.sentinel;

/**
 * 搜索服务路由常量。
 */
public final class SearchRoutes {

    private SearchRoutes() {}

    public static final String PREFIX = "/api/v1/search";
    public static final String POSTS = PREFIX + "/posts";
    public static final String SUGGEST = PREFIX + "/suggest";
    public static final String HOT = PREFIX + "/hot";
    public static final String HISTORY = PREFIX + "/history";
}
