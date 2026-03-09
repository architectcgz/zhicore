package com.zhicore.ranking.infrastructure.sentinel;

/**
 * 排行榜服务 Sentinel 方法级资源常量。
 */
public final class RankingSentinelResources {

    private RankingSentinelResources() {
    }

    public static final String HOT_POST_DETAILS = "ranking:getHotPostsWithDetails";
    public static final String RESOLVE_POST_METADATA = "ranking:resolvePostMetadata";
}
