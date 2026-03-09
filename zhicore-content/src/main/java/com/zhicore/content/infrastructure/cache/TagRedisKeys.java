package com.zhicore.content.infrastructure.cache;

import com.zhicore.common.cache.CacheConstants;

/**
 * Tag Service Redis Key 定义
 * 
 * 命名规范：tag:{entity}:{id}:{field}
 * 示例：tag:slug:postgresql, tag:posts:123
 *
 * @author ZhiCore Team
 */
public final class TagRedisKeys {

    private static String prefix() {
        return CacheConstants.withNamespace("tag");
    }
    
    /**
     * 热门标签缓存键前缀
     * 用于批量清除所有热门标签缓存
     */
    private static String hotTagsPrefix() {
        return CacheConstants.withNamespace("tags") + ":hot:";
    }

    private TagRedisKeys() {
    }

    /**
     * Tag 详情缓存（通过 slug 查询）
     * Key: tag:slug:{slug}
     * TTL: 1 hour
     */
    public static String bySlug(String slug) {
        return prefix() + ":slug:" + slug;
    }

    /**
     * Tag 详情缓存（通过 ID 查询）
     * Key: tag:id:{tagId}
     * TTL: 1 hour
     */
    public static String byId(Long tagId) {
        return prefix() + ":id:" + tagId;
    }

    /**
     * Post 的 Tag 列表缓存
     * Key: post:tags:{postId}
     * TTL: 30 minutes
     */
    public static String postTags(Long postId) {
        return CacheConstants.withNamespace("post") + ":tags:" + postId;
    }

    /**
     * Tag 下的 Post ID 列表缓存（分页）
     * Key: tag:posts:{tagId}:page:{page}:{size}
     * TTL: 30 minutes
     */
    public static String tagPosts(Long tagId, int page, int size) {
        return prefix() + ":posts:" + tagId + ":page:" + page + ":" + size;
    }

    /**
     * 热门标签缓存
     * Key: tags:hot:{limit}
     * TTL: 1 hour
     */
    public static String hotTags(int limit) {
        return hotTagsPrefix() + limit;
    }

    /**
     * 热门标签缓存批量失效匹配模式
     * Pattern: tags:hot:*
     */
    public static String hotTagsPattern() {
        return hotTagsPrefix() + "*";
    }

    /**
     * Tag 统计信息缓存
     * Key: tag:stats:{tagId}
     * TTL: 1 hour
     */
    public static String tagStats(Long tagId) {
        return prefix() + ":stats:" + tagId;
    }

    // ==================== 分布式锁 ====================

    /**
     * Tag 查询锁键
     * Key: tag:lock:slug:{slug}
     */
    public static String lockBySlug(String slug) {
        return prefix() + ":lock:slug:" + slug;
    }

    /**
     * 热门标签查询锁键
     * Key: tag:lock:hot:{limit}
     */
    public static String lockHotTags(int limit) {
        return prefix() + ":lock:hot:" + limit;
    }
}
