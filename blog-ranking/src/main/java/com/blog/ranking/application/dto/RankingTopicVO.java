package com.blog.ranking.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 排行榜话题视图对象
 *
 * @author Blog Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingTopicVO {

    /**
     * 话题ID
     */
    private Long topicId;

    /**
     * 排名
     */
    private int rank;

    /**
     * 热度分数
     */
    private double score;

    /**
     * 话题名称
     */
    private String name;

    /**
     * 话题描述
     */
    private String description;

    /**
     * 文章数
     */
    private int postCount;

    /**
     * 关注数
     */
    private int followCount;
}
