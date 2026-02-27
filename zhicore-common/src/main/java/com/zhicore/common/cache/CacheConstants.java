package com.zhicore.common.cache;

/**
 * 缓存常量定义
 * 
 * Redis Key 命名规范：{service}:{entity}:{id}:{field}
 *
 * @author ZhiCore Team
 */
public final class CacheConstants {

    private CacheConstants() {
    }

    // ==================== 全局命名空间 ====================

    /**
     * 全局 Redis Key 命名空间前缀
     * 用于多环境隔离（如 dev/staging/prod 共用同一 Redis 集群时）
     * 可通过 cache.key-namespace 配置覆盖
     */
    private static String globalNamespace = "zhicore";

    /**
     * 获取全局命名空间前缀
     */
    public static String getGlobalNamespace() {
        return globalNamespace;
    }

    /**
     * 设置全局命名空间前缀（由 CacheNamespaceInitializer 在启动时调用）
     */
    public static void setGlobalNamespace(String namespace) {
        if (namespace != null && !namespace.isBlank()) {
            globalNamespace = namespace;
        }
    }

    /**
     * 拼接全局前缀和服务前缀
     */
    public static String withNamespace(String servicePrefix) {
        return globalNamespace + SEPARATOR + servicePrefix;
    }

    // ==================== Key 前缀 ====================

    /**
     * 用户服务前缀
     */
    public static final String USER_PREFIX = "user";

    /**
     * 文章服务前缀
     */
    public static final String POST_PREFIX = "post";

    /**
     * 评论服务前缀
     */
    public static final String COMMENT_PREFIX = "comment";

    /**
     * 消息服务前缀
     */
    public static final String MESSAGE_PREFIX = "message";

    /**
     * 通知服务前缀
     */
    public static final String NOTIFICATION_PREFIX = "notification";

    // ==================== Key 分隔符 ====================

    /**
     * Key 分隔符
     */
    public static final String SEPARATOR = ":";

    // ==================== 空值标记 ====================

    /**
     * 空值缓存标记
     */
    public static final String NULL_VALUE = "NULL";

    // ==================== 锁前缀 ====================

    /**
     * 分布式锁前缀
     */
    public static final String LOCK_PREFIX = "lock";

    // ==================== 缓存抖动配置 ====================

    /**
     * 最大抖动秒数
     */
    public static final int MAX_JITTER_SECONDS = 60;

    /**
     * 空值缓存 TTL（秒）
     */
    public static final long NULL_VALUE_TTL_SECONDS = 60;

    // ==================== Tag 缓存 TTL ====================

    /**
     * Tag 详情缓存 TTL（秒）
     * 1 hour
     */
    public static final long TAG_CACHE_TTL_SECONDS = 3600;

    /**
     * Post-Tag 关联缓存 TTL（秒）
     * 30 minutes
     */
    public static final long POST_TAG_CACHE_TTL_SECONDS = 1800;

    /**
     * 热门标签缓存 TTL（秒）
     * 1 hour
     */
    public static final long HOT_TAGS_CACHE_TTL_SECONDS = 3600;
}
