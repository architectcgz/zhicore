package com.zhicore.content.infrastructure.cache;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;

/**
 * 文章服务 Redis Key 定义
 *
 * 命名规范：{@code {service}:{scope}:{...}}
 * 示例：{@code post:detail:123}、{@code post:list:v2:status:PUBLISHED:sort:LATEST:size:20:page:1}
 *
 * 约束（R12）：
 * - 列表类 key 必须包含：status / sort / size / cursor|page，避免跨维度污染；
 * - status 为空时使用 ALL 占位；
 * - 结构升级时通过版本前缀 bump（例如 v1 -> v2）。
 */
public final class PostRedisKeys {

    private PostRedisKeys() {
        // 工具类，禁止实例化
    }

    private static String prefix() {
        return CacheConstants.withNamespace("post");
    }

    /**
     * 列表缓存 Key 版本前缀（需要升级结构或维度时 bump）
     */
    private static final String LIST_VERSION = "v2";

    // ==================== 文章缓存 ====================

    /**
     * 文章详情缓存
     * Key: post:detail:{postId}
     * 
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String detail(PostId postId) {
        return prefix() + ":detail:" + postId.getValue();
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
        return prefix() + ":content:" + postId.getValue();
    }

    public static String content(Long postId) {
        return content(PostId.of(postId));
    }

    private static String listLatestPrefix() {
        return prefix() + ":list:" + LIST_VERSION + ":status:PUBLISHED:sort:LATEST";
    }

    /**
     * 最新文章列表缓存前缀（用于 pattern 删除）
     */
    public static String listLatest() {
        return listLatestPrefix();
    }

    /**
     * 最新文章列表缓存（偏移分页：page）
     */
    public static String listLatest(int pageNumber, int size) {
        return listLatestPrefix() + ":size:" + size + ":page:" + pageNumber;
    }

    /**
     * 最新文章列表缓存（游标分页：cursor）
     *
     * 说明：cursor 为空时使用 INIT 占位，避免空值导致 key 结构不稳定。
     */
    public static String listLatestCursor(String cursor, int size) {
        String safeCursor = cursor == null || cursor.isBlank() ? "INIT" : cursor.trim();
        return listLatestPrefix() + ":size:" + size + ":cursor:" + safeCursor;
    }

    /**
     * 最新文章列表缓存 pattern（用于 deletePattern）
     */
    public static String listLatestPattern() {
        return listLatestPrefix() + ":*";
    }

    private static String listAuthorPrefix(UserId authorId) {
        return prefix() + ":list:" + LIST_VERSION + ":author:" + authorId.getValue() + ":status:ALL:sort:LATEST";
    }

    /**
     * 作者文章列表缓存前缀（用于 pattern 删除）
     */
    public static String listAuthor(UserId authorId) {
        return listAuthorPrefix(authorId);
    }

    public static String listAuthor(Long authorId) {
        return listAuthor(UserId.of(authorId));
    }

    /**
     * 作者文章列表缓存（偏移分页：page）
     */
    public static String listAuthor(UserId authorId, int pageNumber, int size) {
        return listAuthorPrefix(authorId) + ":size:" + size + ":page:" + pageNumber;
    }

    /**
     * 作者文章列表缓存 pattern（用于 deletePattern）
     */
    public static String listAuthorPattern(UserId authorId) {
        return listAuthorPrefix(authorId) + ":*";
    }

    private static String listTagPrefix(TagId tagId) {
        return prefix() + ":list:" + LIST_VERSION + ":tag:" + tagId.getValue() + ":status:PUBLISHED:sort:LATEST";
    }

    /**
     * 标签文章列表缓存前缀（用于 pattern 删除）
     */
    public static String listTag(TagId tagId) {
        return listTagPrefix(tagId);
    }

    public static String listTag(Long tagId) {
        return listTag(TagId.of(tagId));
    }

    /**
     * 标签文章列表缓存（偏移分页：page）
     */
    public static String listTag(TagId tagId, int pageNumber, int size) {
        return listTagPrefix(tagId) + ":size:" + size + ":page:" + pageNumber;
    }

    /**
     * 标签文章列表缓存 pattern（用于 deletePattern）
     */
    public static String listTagPattern(TagId tagId) {
        return listTagPrefix(tagId) + ":*";
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
        return prefix() + ":" + postId.getValue() + ":stats:views";
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
        return prefix() + ":" + postId.getValue() + ":stats:likes";
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
        return prefix() + ":" + postId.getValue() + ":stats:comments";
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
        return prefix() + ":" + postId.getValue() + ":stats:favorites";
    }

    public static String favoriteCount(Long postId) {
        return favoriteCount(PostId.of(postId));
    }

    public static String presenceSessions(Long postId) {
        return prefix() + ":presence:" + postId + ":sessions";
    }

    public static String presenceSession(String sessionId) {
        return prefix() + ":presence:session:" + sessionId;
    }

    public static String presenceAnonymousRegisterThrottle(Long postId, String fingerprint) {
        return prefix() + ":presence:" + postId + ":anon:register:" + fingerprint;
    }

    public static String presenceHeartbeatThrottle(Long postId, String sessionId) {
        return prefix() + ":presence:" + postId + ":heartbeat:" + sessionId;
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
        return prefix() + ":lock:detail:" + postId.getValue();
    }

    /**
     * 文章发布锁键
     * Key: post:lock:publish:{postId}
     * 
     * @param postId 文章 ID
     * @return Redis key
     */
    public static String lockPublish(PostId postId) {
        return prefix() + ":lock:publish:" + postId.getValue();
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
        return prefix() + ":*:" + postId.getValue();
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
        return prefix() + ":user:" + userId + ":liked:" + postId;
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
        return prefix() + ":user:" + userId + ":favorited:" + postId;
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
        return prefix() + ":draft:" + postId + ":" + userId;
    }

    /**
     * 用户草稿列表缓存
     * Key: post:drafts:user:{userId}
     * 
     * @param userId 用户 ID
     * @return Redis key
     */
    public static String userDrafts(Long userId) {
        return prefix() + ":drafts:user:" + userId;
    }
}
