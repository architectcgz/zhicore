package com.zhicore.user.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户关注持久化对象
 * 使用复合主键 (followerId, followingId)
 *
 * @author ZhiCore Team
 */
@Data
@TableName("user_follows")
public class UserFollowPO {

    private Long followerId;

    private Long followingId;

    private OffsetDateTime createdAt;
}
