package com.zhicore.comment.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 评论统计值对象（不可变）
 *
 * @author ZhiCore Team
 */
@Getter
public final class CommentStats {

    /**
     * 点赞数
     */
    private final int likeCount;

    /**
     * 回复数
     */
    private final int replyCount;

    @JsonCreator
    public CommentStats(
            @JsonProperty("likeCount") int likeCount,
            @JsonProperty("replyCount") int replyCount) {
        this.likeCount = Math.max(0, likeCount);
        this.replyCount = Math.max(0, replyCount);
    }

    /**
     * 创建空统计
     */
    public static CommentStats empty() {
        return new CommentStats(0, 0);
    }

    /**
     * 增加点赞数
     */
    public CommentStats incrementLikes() {
        return new CommentStats(likeCount + 1, replyCount);
    }

    /**
     * 减少点赞数
     */
    public CommentStats decrementLikes() {
        return new CommentStats(Math.max(0, likeCount - 1), replyCount);
    }

    /**
     * 增加回复数
     */
    public CommentStats incrementReplies() {
        return new CommentStats(likeCount, replyCount + 1);
    }

    /**
     * 减少回复数
     */
    public CommentStats decrementReplies() {
        return new CommentStats(likeCount, Math.max(0, replyCount - 1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommentStats that = (CommentStats) o;
        return likeCount == that.likeCount && replyCount == that.replyCount;
    }

    @Override
    public int hashCode() {
        int result = likeCount;
        result = 31 * result + replyCount;
        return result;
    }

    @Override
    public String toString() {
        return "CommentStats{" +
                "likeCount=" + likeCount +
                ", replyCount=" + replyCount +
                '}';
    }
}
