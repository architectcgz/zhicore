package com.zhicore.content.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 文章统计模型（独立实体）
 * 
 * 通过消息队列异步更新，实现最终一致性。
 * 与 Post 聚合分离，避免强一致性带来的性能问题。
 *
 * @author ZhiCore Team
 */
@Getter
public final class PostStats {

    /**
     * 文章ID（值对象）
     */
    private final PostId postId;

    /**
     * 浏览量
     */
    private final int viewCount;

    /**
     * 点赞数
     */
    private final int likeCount;

    /**
     * 评论数
     */
    private final int commentCount;

    /**
     * 分享数
     */
    private final int shareCount;

    /**
     * 最后更新时间
     */
    private final LocalDateTime lastUpdatedAt;

    /**
     * 构造函数
     * 使用 @JsonCreator 支持 Jackson 反序列化
     */
    @JsonCreator
    public PostStats(@JsonProperty("postId") PostId postId,
                     @JsonProperty("viewCount") Integer viewCount,
                     @JsonProperty("likeCount") Integer likeCount,
                     @JsonProperty("commentCount") Integer commentCount,
                     @JsonProperty("shareCount") Integer shareCount,
                     @JsonProperty("lastUpdatedAt") LocalDateTime lastUpdatedAt) {
        this.postId = postId;
        this.viewCount = viewCount != null ? Math.max(0, viewCount) : 0;
        this.likeCount = likeCount != null ? Math.max(0, likeCount) : 0;
        this.commentCount = commentCount != null ? Math.max(0, commentCount) : 0;
        this.shareCount = shareCount != null ? Math.max(0, shareCount) : 0;
        this.lastUpdatedAt = lastUpdatedAt != null ? lastUpdatedAt : LocalDateTime.now();
    }

    /**
     * 创建空统计
     * 
     * @param postId 文章ID（值对象）
     * @return PostStats 实例
     */
    public static PostStats empty(PostId postId) {
        return new PostStats(postId, 0, 0, 0, 0, LocalDateTime.now());
    }

    /**
     * 创建空统计（兼容旧代码）
     * 
     * @deprecated 使用 {@link #empty(PostId)} 代替
     */
    @Deprecated
    public static PostStats empty() {
        return new PostStats(null, 0, 0, 0, 0, LocalDateTime.now());
    }

    /**
     * 覆盖式更新统计数据
     * 
     * @param viewCount 浏览量
     * @param likeCount 点赞数
     * @param commentCount 评论数
     * @param shareCount 分享数
     * @return 新的 PostStats 实例
     */
    public PostStats updateCounts(int viewCount, int likeCount, int commentCount, int shareCount) {
        return new PostStats(
            this.postId,
            viewCount,
            likeCount,
            commentCount,
            shareCount,
            LocalDateTime.now()
        );
    }

    /**
     * 增加浏览量
     */
    public PostStats incrementViews() {
        return new PostStats(postId, viewCount + 1, likeCount, commentCount, shareCount, LocalDateTime.now());
    }

    /**
     * 增加点赞数
     */
    public PostStats incrementLikes() {
        return new PostStats(postId, viewCount, likeCount + 1, commentCount, shareCount, LocalDateTime.now());
    }

    /**
     * 减少点赞数
     */
    public PostStats decrementLikes() {
        return new PostStats(postId, viewCount, Math.max(0, likeCount - 1), commentCount, shareCount, LocalDateTime.now());
    }

    /**
     * 增加评论数
     */
    public PostStats incrementComments() {
        return new PostStats(postId, viewCount, likeCount, commentCount + 1, shareCount, LocalDateTime.now());
    }

    /**
     * 减少评论数
     */
    public PostStats decrementComments() {
        return new PostStats(postId, viewCount, likeCount, Math.max(0, commentCount - 1), shareCount, LocalDateTime.now());
    }

    /**
     * 增加分享数
     */
    public PostStats incrementShares() {
        return new PostStats(postId, viewCount, likeCount, commentCount, shareCount + 1, LocalDateTime.now());
    }

    /**
     * 兼容旧命名（favoriteCount 映射为 shareCount）
     */
    public int getFavoriteCount() {
        return shareCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostStats postStats = (PostStats) o;
        return postId.equals(postStats.postId);
    }

    @Override
    public int hashCode() {
        return postId.hashCode();
    }

    @Override
    public String toString() {
        return "PostStats{" +
                "postId=" + postId +
                ", viewCount=" + viewCount +
                ", likeCount=" + likeCount +
                ", commentCount=" + commentCount +
                ", shareCount=" + shareCount +
                ", lastUpdatedAt=" + lastUpdatedAt +
                '}';
    }
}
