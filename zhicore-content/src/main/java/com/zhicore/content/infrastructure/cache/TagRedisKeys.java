package com.zhicore.content.infrastructure.cache;

/**
 * Tag Service Redis Key 定义
 * 
 * 命名规范：tag:{entity}:{id}:{field}
 * 示例：tag:slug:postgresql, tag:posts:123
 *
 * @author ZhiCore Team
 */
public final class TagRedisKeys {

    private static final String PREFIX = "tag";
    
    /**
     * 热门标签缓存键前缀
     * 用于批量清除所有热门标签缓存
     */
    public static final String HOT_TAGS_PREFIX = "tags:hot:";

    private TagRedisKeys() {
    }

    /**
     * Tag 详情缓存（通过 slug 查询）
     * Key: tag:slug:{slug}
     * TTL: 1 hour
     */
    public static String bySlug(String slug) {
        return PREFIX + ":slug:" + slug;
    }

    /**
     * Tag 详情缓存（通过 ID 查询）
     * Key: tag:id:{tagId}
     * TTL: 1 hour
     */
    public static String byId(Long tagId) {
        return PREFIX + ":id:" + tagId;
    }

    /**
     * Post 的 Tag 列表缓存
     * Key: post:tags:{postId}
     * TTL: 30 minutes
     */
    public static String postTags(Long postId) {
        return "post:tags:" + postId;
    }

    /**
     * Tag 下的 Post ID 列表缓存（分页）
     * Key: tag:posts:{tagId}:page:{page}:{size}
     * TTL: 30 minutes
     */
    public static String tagPosts(Long tagId, int page, int size) {
        return PREFIX + ":posts:" + tagId + ":page:" + page + ":" + size;
    }

    /**
     * 热门标签缓存
     * Key: tags:hot:{limit}
     * TTL: 1 hour
     */
    public static String hotTags(int limit) {
        return HOT_TAGS_PREFIX + limit;
    }

    /**
     * Tag 统计信息缓存
     * Key: tag:stats:{tagId}
     * TTL: 1 hour
     */
    public static String tagStats(Long tagId) {
        return PREFIX + ":stats:" + tagId;
    }

    // ==================== 分布式锁 ====================

    /**
     * Tag 查询锁键
     * Key: tag:lock:slug:{slug}
     */
    public static String lockBySlug(String slug) {
        return PREFIX + ":lock:slug:" + slug;
    }

    /**
     * 热门标签查询锁键
     * Key: tag:lock:hot:{limit}
     */
    public static String lockHotTags(int limit) {
        return PREFIX + ":lock:hot:" + limit;
    }
}
