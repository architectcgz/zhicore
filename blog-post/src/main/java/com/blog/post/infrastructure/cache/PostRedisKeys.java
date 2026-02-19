package com.blog.post.infrastructure.cache;

/**
 * Post Service Redis Key 定义
 * 
 * 命名规范：{service}:{id}:{entity}:{field}
 * 示例：post:123:detail, post:123:stats:likes
 *
 * @author Blog Team
 */
public final class PostRedisKeys {

    private static final String PREFIX = "post";

    private PostRedisKeys() {
    }

    /**
     * 文章详情缓存
     * Key: post:{postId}:detail
     */
    public static String detail(Long postId) {
        return PREFIX + ":" + postId + ":detail";
    }

    /**
     * 点赞数
     * Key: post:{postId}:stats:likes
     */
    public static String likeCount(Long postId) {
        return PREFIX + ":" + postId + ":stats:likes";
    }

    /**
     * 用户是否点赞文章
     * Key: post:{postId}:liked:{userId}
     */
    public static String userLiked(Long userId, Long postId) {
        return PREFIX + ":" + postId + ":liked:" + userId;
    }

    /**
     * 收藏数
     * Key: post:{postId}:stats:favorites
     */
    public static String favoriteCount(Long postId) {
        return PREFIX + ":" + postId + ":stats:favorites";
    }

    /**
     * 用户是否收藏文章
     * Key: post:{postId}:favorited:{userId}
     */
    public static String userFavorited(Long userId, Long postId) {
        return PREFIX + ":" + postId + ":favorited:" + userId;
    }

    /**
     * 评论数
     * Key: post:{postId}:stats:comments
     */
    public static String commentCount(Long postId) {
        return PREFIX + ":" + postId + ":stats:comments";
    }

    /**
     * 浏览数
     * Key: post:{postId}:stats:views
     */
    public static String viewCount(Long postId) {
        return PREFIX + ":" + postId + ":stats:views";
    }

    /**
     * 文章列表缓存
     * Key: post:list:{type}:{page}
     */
    public static String list(String type, int page) {
        return PREFIX + ":list:" + type + ":" + page;
    }

    /**
     * 用户文章列表缓存
     * Key: post:user:{userId}:list:{status}:{page}
     */
    public static String userPostList(Long userId, String status, int page) {
        return PREFIX + ":user:" + userId + ":list:" + status + ":" + page;
    }

    /**
     * 文章内容缓存（MongoDB）
     * Key: post:{postId}:content
     */
    public static String content(Long postId) {
        return PREFIX + ":" + postId + ":content";
    }

    /**
     * 文章完整详情缓存（元数据 + 内容）
     * Key: post:{postId}:full
     */
    public static String fullDetail(Long postId) {
        return PREFIX + ":" + postId + ":full";
    }

    /**
     * 用户草稿缓存
     * Key: post:draft:{postId}:{userId}
     */
    public static String draft(Long postId, Long userId) {
        return PREFIX + ":draft:" + postId + ":" + userId;
    }

    /**
     * 用户所有草稿列表缓存
     * Key: post:drafts:{userId}
     */
    public static String userDrafts(Long userId) {
        return PREFIX + ":drafts:" + userId;
    }

    // ==================== 分布式锁 ====================

    /**
     * 文章内容锁键
     * Key: post:lock:content:{postId}
     */
    public static String lockContent(Long postId) {
        return PREFIX + ":lock:content:" + postId;
    }

    /**
     * 文章完整详情锁键
     * Key: post:lock:full:{postId}
     */
    public static String lockFullDetail(Long postId) {
        return PREFIX + ":lock:full:" + postId;
    }
}
