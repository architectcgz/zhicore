package com.zhicore.user.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关注统计视图对象
 *
 * @author ZhiCore Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FollowStatsVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 粉丝数
     */
    private Integer followersCount;

    /**
     * 关注数
     */
    private Integer followingCount;

    /**
     * 是否已关注（当前用户是否关注了该用户）
     */
    private Boolean isFollowing;

    /**
     * 是否被关注（该用户是否关注了当前用户）
     */
    private Boolean isFollowed;
}
