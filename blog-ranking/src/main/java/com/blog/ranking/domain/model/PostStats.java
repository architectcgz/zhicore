package com.blog.ranking.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 文章统计数据（用于热度计算）
 *
 * @author Blog Team
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostStats {

    /**
     * 浏览量
     */
    private long viewCount;

    /**
     * 点赞数
     */
    private int likeCount;

    /**
     * 评论数
     */
    private int commentCount;

    /**
     * 收藏数
     */
    private int favoriteCount;

    /**
     * 创建默认统计
     */
    public static PostStats empty() {
        return PostStats.builder()
                .viewCount(0)
                .likeCount(0)
                .commentCount(0)
                .favoriteCount(0)
                .build();
    }
}
