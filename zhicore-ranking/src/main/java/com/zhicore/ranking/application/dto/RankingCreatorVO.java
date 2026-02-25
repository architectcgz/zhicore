package com.zhicore.ranking.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 排行榜创作者视图对象
 *
 * @author ZhiCore Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingCreatorVO {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 排名
     */
    private int rank;

    /**
     * 热度分数
     */
    private double score;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 粉丝数
     */
    private int followersCount;

    /**
     * 文章数
     */
    private int postCount;

    /**
     * 总点赞数
     */
    private long totalLikes;
}
