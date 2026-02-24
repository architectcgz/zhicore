package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;

/**
 * 文章服务 Redis Key 定义
 * 
 * 命名规范：{service}:{id}:{entity}:{field}
 * 示例：post:123:detail, post:list:latest
 *
 * @author ZhiCore Team
 */
public final class PostRedisKeys {

    private PostRedisKeys() {
        // 工具类，禁止实例化
    }

    private static final String PREFIX = "post";

    // ==================== 文章缓存 ====================

    /**
     * 文章详情缓存
     * Key: post:detail:{postId}
     * 
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String detail(PostId postId) {
        return PREFIX + ":detail:" + postId.getValue();
    }

    public static String detail(Long postId) {
        return detail(PostId.of(postId));
    }

    /**
     * 文章内容缓存
     * Key: post:content:{postId}
     * 
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String content(PostId postId) {
        return PREFIX + ":content:" + postId.getValue();
    }

    public static String content(Long postId) {
        return content(PostId.of(postId));
    }

    /**
     * 最新文章列表缓存
     * Key: post:list:latest
     * 
     * @return Redis key
     */
    public static String listLatest() {
        return PREFIX + ":list:latest";
    }

    /**
     * 最新文章列表缓存（分页）
     * Key: post:list:latest:{pageNumber}
     * 
     * @param pageNumber 页码
     * @return Redis key
     */
    public static String listLatest(int pageNumber) {
        return PREFIX + ":list:latest:" + pageNumber;
    }

    /**
     * 作者文章列表缓存
     * Key: post:list:author:{authorId}
     * 
     * @param authorId 作者 ID
     * @return Redis key
     */
    public static String listAuthor(UserId authorId) {
        return PREFIX + ":list:author:" + authorId.getValue();
    }

    public static String listAuthor(Long authorId) {
        return listAuthor(UserId.of(authorId));
    }

    /**
     * 作者文章列表缓存（分页）
     * Key: post:list:author:{authorId}:{pageNumber}
     * 
     * @param authorId 作者 ID
     * @param pageNumber 页码
     * @return Redis key
     */
    public static String listAuthor(UserId authorId, int pageNumber) {
        return PREFIX + ":list:author:" + authorId.getValue() + ":" + pageNumber;
    }

    /**
     * 标签文章列表缓存
     * Key: post:list:tag:{tagId}
     * 
     * @param tagId 标签 ID
     * @return Redis key
     */
    public static String listTag(TagId tagId) {
        return PREFIX + ":list:tag:" + tagId.getValue();
    }

    public static String listTag(Long tagId) {
        return listTag(TagId.of(tagId));
    }

    /**
     * 标签文章列表缓存（分页）
     * Key: post:list:tag:{tagId}:{pageNumber}
     * 
     * @param tagId 标签 ID
     * @param pageNumber 页码
     * @return Redis key
     */
    public static String listTag(TagId tagId, int pageNumber) {
        return PREFIX + ":list:tag:" + tagId.getValue() + ":" + pageNumber;
    }

    // ==================== 文章统计 ====================

    /**
     * 文章浏览数
     * Key: post:{postId}:stats:views
     * 
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String viewCount(PostId postId) {
        return PREFIX + ":" + postId.getValue() + ":stats:views";
    }

    public static String viewCount(Long postId) {
        return viewCount(PostId.of(postId));
    }

    /**
     * 文章点赞数
     * Key: post:{postId}:stats:likes
     * 
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String likeCount(PostId postId) {
        return PREFIX + ":" + postId.getValue() + ":stats:likes";
    }

    public static String likeCount(Long postId) {
        return likeCount(PostId.of(postId));
    }

    /**
     * 文章评论数
     * Key: post:{postId}:stats:comments
     * 
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String commentCount(PostId postId) {
        return PREFIX + ":" + postId.getValue() + ":stats:comments";
    }

    public static String commentCount(Long postId) {
        return commentCount(PostId.of(postId));
    }

    /**
     * 文章收藏数
     * Key: post:{postId}:stats:favorites
     * 
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String favoriteCount(PostId postId) {
        return PREFIX + ":" + postId.getValue() + ":stats:favorites";
    }

    public static String favoriteCount(Long postId) {
        return favoriteCount(PostId.of(postId));
    }

    // ==================== 分布式锁 ====================

    /**
     * 文章详情锁键
     * Key: post:lock:detail:{postId}
     * 
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String lockDetail(PostId postId) {
        return PREFIX + ":lock:detail:" + postId.getValue();
    }

    /**
     * 文章发布锁键
     * Key: post:lock:publish:{postId}
     * 
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String lockPublish(PostId postId) {
        return PREFIX + ":lock:publish:" + postId.getValue();
    }

    // ==================== 模式匹配 ====================

    /**
     * 文章所有相关缓存的模式匹配键
     * Key: post:*:{postId}
     * 
     * @param postId 文章 ID
     * @return Redis key 模式
     */
    public static String allRelatedPattern(PostId postId) {
        return PREFIX + ":*:" + postId.getValue();
    }

    public static String allRelatedPattern(Long postId) {
        return allRelatedPattern(PostId.of(postId));
    }

    // ==================== 用户交互缓存 ====================

    /**
     * 用户点赞标记
     * Key: post:user:{userId}:liked:{postId}
     * 
     * @param userId 用户 ID
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String userLiked(Long userId, Long postId) {
        return PREFIX + ":user:" + userId + ":liked:" + postId;
    }

    /**
     * 用户收藏标记
     * Key: post:user:{userId}:favorited:{postId}
     * 
     * @param userId 用户 ID
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String userFavorited(Long userId, Long postId) {
        return PREFIX + ":user:" + userId + ":favorited:" + postId;
    }

    // ==================== 草稿缓存 ====================

    /**
     * 文章草稿缓存
     * Key: post:draft:{postId}:{userId}
     * 
     * @param postId 文章 ID
     * @param userId 用户 ID
     * @return Redis key
     */
    public static String draft(Long postId, Long userId) {
        return PREFIX + ":draft:" + postId + ":" + userId;
    }

    /**
     * 用户草稿列表缓存
     * Key: post:drafts:user:{userId}
     * 
     * @param userId 用户 ID
     * @return Redis key
     */
    public static String userDrafts(Long userId) {
        return PREFIX + ":drafts:user:" + userId;
    }
}
