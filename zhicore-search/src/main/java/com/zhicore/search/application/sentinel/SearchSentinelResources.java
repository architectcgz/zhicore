package com.zhicore.search.application.sentinel;

/**
 * 搜索服务 Sentinel 方法级资源常量。
 */
public final class SearchSentinelResources {

    private SearchSentinelResources() {
    }

    public static final String SEARCH_POSTS = "search:searchPosts";
    public static final String GET_SUGGESTIONS = "search:getSuggestions";
    public static final String GET_HOT_KEYWORDS = "search:getHotKeywords";
    public static final String GET_USER_HISTORY = "search:getUserHistory";
}
