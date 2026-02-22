package com.zhicore.ranking.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 创作者统计数据（用于热度计算）
 *
 * @author ZhiCore Team
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorStats {

    /**
     * 粉丝数
     */
    private int followersCount;

    /**
     * 总点赞数
     */
    private long totalLikes;

    /**
     * 总评论数
     */
    private long totalComments;

    /**
     * 文章数
     */
    private int postCount;

    /**
     * 创建默认统计
     */
    public static CreatorStats empty() {
        return CreatorStats.builder()
                .followersCount(0)
                .totalLikes(0)
                .totalComments(0)
                .postCount(0)
                .build();
    }
}
