package com.blog.user.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户拉黑持久化对象
 * 使用复合主键 (blockerId, blockedId)
 *
 * @author Blog Team
 */
@Data
@TableName("user_blocks")
public class UserBlockPO {

    private Long blockerId;

    private Long blockedId;

    private OffsetDateTime createdAt;
}
