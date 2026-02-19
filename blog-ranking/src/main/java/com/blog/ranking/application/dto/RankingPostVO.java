package com.blog.ranking.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 排行榜文章视图对象
 *
 * @author Blog Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingPostVO {

    /**
     * 文章ID
     */
    private String postId;

    /**
     * 排名
     */
    private int rank;

    /**
     * 热度分数
     */
    private double score;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 作者ID
     */
    private String authorId;

    /**
     * 作者名称
     */
    private String authorName;

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
}
