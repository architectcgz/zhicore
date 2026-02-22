package com.zhicore.common.constant;

/**
 * 通用常量
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /**
     * HTTP Header 常量
     */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * 分页默认值
     */
    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 混合分页阈值（页码超过此值使用游标分页）
     */
    public static final int HYBRID_PAGINATION_THRESHOLD = 5;

    /**
     * 缓存相关
     */
    public static final String CACHE_NULL_VALUE = "NULL";
    public static final long CACHE_NULL_TTL_SECONDS = 60;

    /**
     * 分布式锁前缀
     */
    public static final String LOCK_PREFIX = "lock:";

    /**
     * 消息队列相关
     */
    public static final String MQ_PROCESSED_PREFIX = "mq:processed:";
}
