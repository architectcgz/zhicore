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
