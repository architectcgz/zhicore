package com.blog.user.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户关注统计持久化对象
 *
 * @author Blog Team
 */
@Data
@TableName("user_follow_stats")
public class UserFollowStatsPO {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private Integer followersCount;

    private Integer followingCount;
}
