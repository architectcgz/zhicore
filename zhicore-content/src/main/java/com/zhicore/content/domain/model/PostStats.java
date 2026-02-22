package com.zhicore.content.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 文章统计值对象（不可变）
 *
 * @author ZhiCore Team
 */
@Getter
public final class PostStats {

    private final int likeCount;
    private final int commentCount;
    private final int favoriteCount;
    private final long viewCount;

    /**
     * 构造函数
     * 使用 @JsonCreator 支持 Jackson 反序列化
     */
    @JsonCreator
    public PostStats(@JsonProperty("likeCount") int likeCount,
                     @JsonProperty("commentCount") int commentCount,
                     @JsonProperty("favoriteCount") int favoriteCount,
                     @JsonProperty("viewCount") long viewCount) {
        this.likeCount = Math.max(0, likeCount);
        this.commentCount = Math.max(0, commentCount);
        this.favoriteCount = Math.max(0, favoriteCount);
        this.viewCount = Math.max(0, viewCount);
    }

    /**
     * 创建空统计
     */
    public static PostStats empty() {
        return new PostStats(0, 0, 0, 0);
    }

    /**
     * 增加浏览量
     */
    public PostStats incrementViews() {
        return new PostStats(likeCount, commentCount, favoriteCount, viewCount + 1);
    }

    /**
     * 增加点赞数
     */
    public PostStats incrementLikes() {
        return new PostStats(likeCount + 1, commentCount, favoriteCount, viewCount);
    }

    /**
     * 减少点赞数
     */
    public PostStats decrementLikes() {
        return new PostStats(Math.max(0, likeCount - 1), commentCount, favoriteCount, viewCount);
    }

    /**
     * 增加评论数
     */
    public PostStats incrementComments() {
        return new PostStats(likeCount, commentCount + 1, favoriteCount, viewCount);
    }

    /**
     * 减少评论数
     */
    public PostStats decrementComments() {
        return new PostStats(likeCount, Math.max(0, commentCount - 1), favoriteCount, viewCount);
    }

    /**
     * 增加收藏数
     */
    public PostStats incrementFavorites() {
        return new PostStats(likeCount, commentCount, favoriteCount + 1, viewCount);
    }

    /**
     * 减少收藏数
     */
    public PostStats decrementFavorites() {
        return new PostStats(likeCount, commentCount, Math.max(0, favoriteCount - 1), viewCount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostStats postStats = (PostStats) o;
        return likeCount == postStats.likeCount &&
                commentCount == postStats.commentCount &&
                favoriteCount == postStats.favoriteCount &&
                viewCount == postStats.viewCount;
    }

    @Override
    public int hashCode() {
        int result = likeCount;
        result = 31 * result + commentCount;
        result = 31 * result + favoriteCount;
        result = 31 * result + (int) (viewCount ^ (viewCount >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "PostStats{" +
                "likeCount=" + likeCount +
                ", commentCount=" + commentCount +
                ", favoriteCount=" + favoriteCount +
                ", viewCount=" + viewCount +
                '}';
    }
}
