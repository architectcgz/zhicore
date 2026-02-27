package com.zhicore.ranking.infrastructure.sentinel;

/**
 * 排行榜路由常量
 *
 * <p>统一管理 URL 前缀和归一化后的模式路径，供 Controller、Sentinel 限流规则、UrlCleaner 共享，
 * 避免路径硬编码导致的同步风险。</p>
 */
public final class RankingRoutes {

    private RankingRoutes() {}

    /** Controller @RequestMapping 前缀 */
    public static final String PREFIX = "/api/v1/ranking";

    /** 归一化后的路径变量模式：文章 */
    public static final String POSTS_ID = PREFIX + "/posts/:id";
    /** 归一化后的路径变量模式：创作者 */
    public static final String CREATORS_ID = PREFIX + "/creators/:id";
    /** 归一化后的路径变量模式：话题 */
    public static final String TOPICS_ID = PREFIX + "/topics/:id";
}
