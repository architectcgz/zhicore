package com.zhicore.comment.infrastructure.cache;

import com.zhicore.comment.application.dto.CommentSortType;

/**
 * 评论服务 Redis Key 定义
 * 
 * 命名规范：{service}:{id}:{entity}:{field}
 * 示例：comment:123:detail:v2, comment:123:stats:likes
 *
 * @author ZhiCore Team
 */
public final class CommentRedisKeys {

    private CommentRedisKeys() {}

    private static final String PREFIX = "comment";

    /**
     * 评论详情缓存
     * Key: comment:{commentId}:detail:v2
     */
    public static String detail(Long commentId) {
        return PREFIX + ":" + commentId + ":detail:v2";
    }

    /**
     * 评论点赞数
     * Key: comment:{commentId}:stats:likes
     */
    public static String likeCount(Long commentId) {
        return PREFIX + ":" + commentId + ":stats:likes";
    }

    /**
     * 评论回复数
     * Key: comment:{commentId}:stats:replies
     */
    public static String replyCount(Long commentId) {
        return PREFIX + ":" + commentId + ":stats:replies";
    }

    /**
     * 用户是否点赞评论
     * Key: comment:{commentId}:liked:{userId}
     */
    public static String userLiked(Long userId, Long commentId) {
        return PREFIX + ":" + commentId + ":liked:" + userId;
    }

    /**
     * 文章评论数
     * Key: comment:post:{postId}:count
     */
    public static String postCommentCount(Long postId) {
        return PREFIX + ":post:" + postId + ":count";
    }

    /**
     * 文章顶级评论列表（按时间排序）
     * Key: comment:post:{postId}:top:time
     */
    public static String postTopLevelByTime(Long postId) {
        return PREFIX + ":post:" + postId + ":top:time";
    }

    /**
     * 文章顶级评论列表（按热度排序）
     * Key: comment:post:{postId}:top:hot
     */
    public static String postTopLevelByHot(Long postId) {
        return PREFIX + ":post:" + postId + ":top:hot";
    }

    /**
     * 评论回复列表
     * Key: comment:{rootId}:replies
     */
    public static String replies(Long rootId) {
        return PREFIX + ":" + rootId + ":replies";
    }

    /**
     * 首页评论快照缓存。
     * Key: comment:post:{postId}:homepage:{sort}:{size}:{replyLimit}
     */
    public static String homepageSnapshot(Long postId, CommentSortType sortType, int size, int hotRepliesLimit) {
        return PREFIX + ":post:" + postId + ":homepage:" + sortType.name().toLowerCase() + ":" + size + ":" + hotRepliesLimit;
    }

    /**
     * ranking 热门文章候选集。
     * Key: comment:ranking:posts:hot:candidates
     */
    public static String rankingHotPostCandidates() {
        return PREFIX + ":ranking:posts:hot:candidates";
    }

    /**
     * ranking 热门文章候选集元信息。
     * Key: comment:ranking:posts:hot:candidates:meta
     */
    public static String rankingHotPostCandidatesMeta() {
        return PREFIX + ":ranking:posts:hot:candidates:meta";
    }

    // ==================== 分布式锁 ====================

    /**
     * 评论详情锁键
     * Key: comment:lock:detail:{commentId}
     */
    public static String lockDetail(Long commentId) {
        return PREFIX + ":lock:detail:" + commentId;
    }
}
